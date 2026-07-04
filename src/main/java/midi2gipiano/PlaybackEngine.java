package midi2gipiano;

import midi2gipiano.model.MidiNote;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlaybackEngine {

    public enum State { STOPPED, PLAYING, PAUSED }

    public interface Listener {
        void onNoteChanged(int midiNote, int keyIndex, char keyLetter);
        default void onStateChanged(State state) {}
        default void onFinished() {}
        default void onError(String message) {}
    }

    private List<MidiNote> notes;
    private final NoteMapper mapper;
    private final KeyPressSimulator simulator;
    private ScheduledExecutorService scheduler;
    private List<ScheduledFuture<?>> tasks;
    private Listener listener;

    private volatile State state = State.STOPPED;
    private volatile long pausePositionMicros;
    private volatile long totalDurationMicros;
    private volatile long currentPositionMicros;
    private volatile int currentMidiNote;
    private volatile int currentKeyIndex;

    private final AtomicBoolean suspendFlag = new AtomicBoolean(false);

    public PlaybackEngine(NoteMapper mapper, KeyPressSimulator simulator) {
        this.mapper = mapper;
        this.simulator = simulator;
    }

    public void setNotes(List<MidiNote> notes, long totalDurationMicros) {
        stop();
        this.notes = notes;
        this.totalDurationMicros = totalDurationMicros;
        this.currentPositionMicros = 0;
        this.currentMidiNote = -1;
        this.currentKeyIndex = -1;
        mapper.prepare(notes);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void play() {
        if (notes == null || notes.isEmpty()) return;
        stop();
        suspendFlag.set(false);
        state = State.PLAYING;
        notifyStateChanged();

        long fromMicros = pausePositionMicros;
        pausePositionMicros = 0;
        currentPositionMicros = fromMicros;

        int startIndex = 0;
        while (startIndex < notes.size()
                && notes.get(startIndex).endMicros <= fromMicros) {
            startIndex++;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        tasks = new CopyOnWriteArrayList<>();

        for (int i = startIndex; i < notes.size(); i++) {
            if (suspendFlag.get()) break;

            MidiNote note = notes.get(i);
            if (note.endMicros <= fromMicros) continue;

            int keyIndex = mapper.map(note.noteNumber);
            long pressDelay = Math.max(0, note.startMicros - fromMicros);
            long releaseDelay = Math.max(0, note.endMicros - fromMicros);

            final int ki = keyIndex;
            final int mn = note.noteNumber;

            ScheduledFuture<?> pressTask = scheduler.schedule(() -> {
                if (!suspendFlag.get()) {
                    currentMidiNote = mn;
                    currentKeyIndex = ki;
                    currentPositionMicros = note.startMicros;
                    simulator.press(ki);
                    if (listener != null) {
                        listener.onNoteChanged(mn, ki, NoteMapper.keyLetter(ki));
                    }
                }
            }, pressDelay, TimeUnit.MICROSECONDS);

            ScheduledFuture<?> releaseTask = scheduler.schedule(() -> {
                if (!suspendFlag.get()) {
                    simulator.release(ki);
                }
            }, releaseDelay, TimeUnit.MICROSECONDS);

            tasks.add(pressTask);
            tasks.add(releaseTask);
        }

        long remaining = totalDurationMicros - fromMicros;
        if (remaining > 0) {
            tasks.add(scheduler.schedule(() -> {
                if (!suspendFlag.get()) {
                    simulator.releaseAll();
                    state = State.STOPPED;
                    notifyStateChanged();
                    if (listener != null) listener.onFinished();
                }
            }, remaining, TimeUnit.MICROSECONDS));
        }
    }

    public synchronized void pause() {
        if (state != State.PLAYING) return;
        suspendFlag.set(true);
        cancelTasks();
        pausePositionMicros = currentPositionMicros;
        simulator.releaseAll();
        state = State.PAUSED;
        notifyStateChanged();
    }

    public synchronized void stop() {
        suspendFlag.set(true);
        cancelTasks();
        simulator.releaseAll();
        currentPositionMicros = 0;
        currentMidiNote = -1;
        currentKeyIndex = -1;
        pausePositionMicros = 0;
        state = State.STOPPED;
        notifyStateChanged();
    }

    private void cancelTasks() {
        if (tasks != null) {
            for (ScheduledFuture<?> task : tasks) {
                task.cancel(false);
            }
            tasks.clear();
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private void notifyStateChanged() {
        if (listener != null) listener.onStateChanged(state);
    }

    public State getState() { return state; }
    public long getCurrentPositionMicros() { return currentPositionMicros; }
    public long getTotalDurationMicros() { return totalDurationMicros; }
    public int getCurrentMidiNote() { return currentMidiNote; }
    public int getCurrentKeyIndex() { return currentKeyIndex; }
    public boolean isPlaying() { return state == State.PLAYING; }
    public boolean isPaused() { return state == State.PAUSED; }

    public void shutdown() {
        stop();
    }

    public synchronized void togglePlayPause() {
        switch (state) {
            case PLAYING:
                pause();
                break;
            case PAUSED:
                play();
                break;
            case STOPPED:
                play();
                break;
        }
    }
}
