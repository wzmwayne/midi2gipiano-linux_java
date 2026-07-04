package midi2gipiano;

import midi2gipiano.model.MidiNote;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;

public class MidiParser {

    public static class ParsedMidi {
        public final List<MidiNote> notes;
        public final long durationMicros;
        public final int trackCount;
        public final int division;
        public final int tempoChangeCount;

        public ParsedMidi(List<MidiNote> notes, long durationMicros,
                          int trackCount, int division, int tempoChangeCount) {
            this.notes = notes;
            this.durationMicros = durationMicros;
            this.trackCount = trackCount;
            this.division = division;
            this.tempoChangeCount = tempoChangeCount;
        }
    }

    public ParsedMidi parse(File file) throws Exception {
        Sequence seq = MidiSystem.getSequence(file);

        if (seq.getDivisionType() != Sequence.PPQ) {
            throw new IllegalArgumentException("仅支持 PPQ 格式的 MIDI 文件");
        }

        int ppq = seq.getResolution();
        int trackCount = seq.getTracks().length;

        List<long[]> tempoChanges = collectTempoChanges(seq);
        List<MidiNote> rawNotes = collectNotes(seq);

        long maxTick = 0;
        for (MidiNote note : rawNotes) {
            if (note.endTick > maxTick) maxTick = note.endTick;
        }

        List<long[]> tempoSegments = buildTempoSegments(tempoChanges, maxTick, ppq);
        long totalDurationMicros = 0;
        if (!tempoSegments.isEmpty()) {
            long[] last = tempoSegments.get(tempoSegments.size() - 1);
            totalDurationMicros = last[3] + (last[1] - last[0]) * last[2] / ppq;
        }

        for (MidiNote note : rawNotes) {
            note.startMicros = tickToMicros(note.startTick, tempoSegments, ppq);
            note.endMicros = tickToMicros(note.endTick, tempoSegments, ppq);
        }

        rawNotes.sort(Comparator.comparingLong(a -> a.startMicros));

        return new ParsedMidi(rawNotes, totalDurationMicros,
                trackCount, ppq, tempoChanges.size());
    }

    private List<long[]> collectTempoChanges(Sequence seq) {
        List<long[]> changes = new ArrayList<>();
        for (Track track : seq.getTracks()) {
            if (track == null) continue;
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage msg = event.getMessage();
                if (msg instanceof MetaMessage) {
                    MetaMessage meta = (MetaMessage) msg;
                    if (meta.getType() == 0x51) {
                        byte[] data = meta.getData();
                        if (data.length >= 3) {
                            int tempo = ((data[0] & 0xFF) << 16)
                                      | ((data[1] & 0xFF) << 8)
                                      | (data[2] & 0xFF);
                            changes.add(new long[]{event.getTick(), tempo});
                        }
                    }
                }
            }
        }
        changes.sort(Comparator.comparingLong(a -> a[0]));
        return changes;
    }

    private List<MidiNote> collectNotes(Sequence seq) {
        Map<String, Stack<long[]>> pending = new HashMap<>();
        List<MidiNote> notes = new ArrayList<>();

        for (int t = 0; t < seq.getTracks().length; t++) {
            Track track = seq.getTracks()[t];
            if (track == null) continue;

            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage msg = event.getMessage();

                if (msg instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) msg;
                    int cmd = sm.getCommand();
                    int noteNum = sm.getData1();
                    int vel = sm.getData2();
                    int ch = sm.getChannel();
                    long tick = event.getTick();

                    if (cmd == ShortMessage.NOTE_ON && vel > 0) {
                        String key = noteNum + "-" + ch + "-" + t;
                        pending.computeIfAbsent(key, k -> new Stack<>())
                               .push(new long[]{tick, noteNum, vel, ch, t});
                    } else if (cmd == ShortMessage.NOTE_OFF
                            || (cmd == ShortMessage.NOTE_ON && vel == 0)) {
                        String key = noteNum + "-" + ch + "-" + t;
                        Stack<long[]> stack = pending.get(key);
                        if (stack != null && !stack.isEmpty()) {
                            long[] on = stack.pop();
                            notes.add(new MidiNote(
                                    (int) on[1], (int) on[2],
                                    (int) on[3], (int) on[4],
                                    on[0], tick));
                        }
                    }
                }
            }
        }

        for (Stack<long[]> stack : pending.values()) {
            while (!stack.isEmpty()) {
                long[] on = stack.pop();
                notes.add(new MidiNote(
                        (int) on[1], (int) on[2],
                        (int) on[3], (int) on[4],
                        on[0], on[0] + 1));
            }
        }

        return notes;
    }

    private List<long[]> buildTempoSegments(List<long[]> changes, long maxTick, int ppq) {
        List<long[]> segments = new ArrayList<>();
        long prevTick = 0;
        long prevTempo = 500000;

        for (long[] change : changes) {
            long tick = change[0];
            long tempo = change[1];
            if (tick > maxTick) break;
            if (tick > prevTick) {
                segments.add(new long[]{prevTick, tick, prevTempo, 0});
            }
            prevTick = tick;
            prevTempo = tempo;
        }
        if (prevTick < maxTick) {
            segments.add(new long[]{prevTick, maxTick, prevTempo, 0});
        }

        long cumMicros = 0;
        for (long[] seg : segments) {
            seg[3] = cumMicros;
            cumMicros += (seg[1] - seg[0]) * seg[2] / ppq;
        }

        return segments;
    }

    private long tickToMicros(long tick, List<long[]> segments, int ppq) {
        for (long[] seg : segments) {
            if (tick >= seg[0] && tick < seg[1]) {
                return seg[3] + (tick - seg[0]) * seg[2] / ppq;
            }
        }
        if (!segments.isEmpty()) {
            long[] last = segments.get(segments.size() - 1);
            return last[3] + (tick - last[0]) * last[2] / ppq;
        }
        return 0;
    }
}
