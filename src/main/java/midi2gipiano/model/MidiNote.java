package midi2gipiano.model;

public class MidiNote {
    public final int noteNumber;
    public final int velocity;
    public final int channel;
    public final int track;
    public final long startTick;
    public final long endTick;
    public long startMicros;
    public long endMicros;

    public MidiNote(int noteNumber, int velocity, int channel, int track,
                    long startTick, long endTick) {
        this.noteNumber = noteNumber;
        this.velocity = velocity;
        this.channel = channel;
        this.track = track;
        this.startTick = startTick;
        this.endTick = endTick;
    }

    public long durationMicros() {
        return endMicros - startMicros;
    }

    public String noteName() {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = noteNumber / 12 - 1;
        return names[noteNumber % 12] + octave;
    }
}
