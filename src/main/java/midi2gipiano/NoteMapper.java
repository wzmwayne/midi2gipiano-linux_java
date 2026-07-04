package midi2gipiano;

import midi2gipiano.model.MidiNote;
import java.util.List;

public class NoteMapper {

    public static final int KEY_COUNT = 21;

    public static final int MODE_WRAP = 0;
    public static final int MODE_EDGE = 1;
    public static final int MODE_FULL = 2;

    private static final int[] PITCH_TO_DEGREE = {
        0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6
    };

    public static final char[] KEY_LETTERS = {
        'Q', 'W', 'E', 'R', 'T', 'Y', 'U',
        'A', 'S', 'D', 'F', 'G', 'H', 'J',
        'Z', 'X', 'C', 'V', 'B', 'N', 'M'
    };

    private static final String[] KEY_NAMES = {
        "do", "re", "mi", "fa", "so", "la", "ti",
        "do", "re", "mi", "fa", "so", "la", "ti",
        "do", "re", "mi", "fa", "so", "la", "ti"
    };

    private int baseOctave = 4;
    private int transpose  = 0;
    private int mode       = MODE_WRAP;

    private double fullScale  = 1.0;
    private double fullCenter = 0;
    private int    fullShift  = 0;
    private boolean useFull   = false;

    public NoteMapper() {}

    public NoteMapper(int baseOctave, int transpose) {
        this.baseOctave = baseOctave;
        this.transpose = transpose;
    }

    public void setMode(int mode) {
        this.mode = mode;
        this.useFull = false;
    }

    public int getMode() { return mode; }

    /** Pre-scan notes and compute full-compression parameters using adjusted values. */
    public void prepare(List<MidiNote> notes) {
        useFull = false;
        if (mode != MODE_FULL || notes == null || notes.isEmpty()) return;

        int minNote = 127, maxNote = 0;
        for (MidiNote n : notes) {
            int nn = n.noteNumber + transpose;
            if (nn < minNote) minNote = nn;
            if (nn > maxNote) maxNote = nn;
        }
        int range = maxNote - minNote;
        if (range <= 0) return;

        fullScale  = 36.0 / range;
        fullCenter = (minNote + maxNote) / 2.0;

        int cmprMin  = (int)Math.round(fullCenter + (minNote - fullCenter) * fullScale);
        int cmprMax  = (int)Math.round(fullCenter + (maxNote - fullCenter) * fullScale);
        int targetLo = baseOctave * 12;
        int targetHi = targetLo + 35;
        if (cmprMin < targetLo) {
            fullShift = targetLo - cmprMin;
        } else if (cmprMax > targetHi) {
            fullShift = targetHi - cmprMax;
        } else {
            fullShift = 0;
        }
        useFull = true;
    }

    public int map(int midiNoteNumber) {
        int adjusted = midiNoteNumber;

        if (mode == MODE_EDGE) {
            int low  = baseOctave * 12;
            int high = baseOctave * 12 + 35;
            if (adjusted < low) {
                int shift = ((low - adjusted + 11) / 12) * 12;
                adjusted += shift;
            } else if (adjusted > high) {
                int shift = ((adjusted - high + 11) / 12) * 12;
                adjusted -= shift;
            }
        }

        adjusted += transpose;

        if (mode == MODE_FULL && useFull) {
            adjusted = (int)Math.round(fullCenter + (adjusted - fullCenter) * fullScale) + fullShift;
        }

        int octave = adjusted / 12 - 1;
        int pc = ((adjusted % 12) + 12) % 12;
        int degree = PITCH_TO_DEGREE[pc];
        int row = ((octave - baseOctave) % 3 + 3) % 3;
        return row * 7 + degree;
    }

    public int getBaseOctave() { return baseOctave; }
    public void setBaseOctave(int baseOctave) {
        if (this.baseOctave != baseOctave) {
            this.baseOctave = baseOctave;
            useFull = false;
        }
    }

    public int getTranspose() { return transpose; }
    public void setTranspose(int transpose) {
        if (this.transpose != transpose) {
            this.transpose = transpose;
            useFull = false;
        }
    }

    public static char keyLetter(int keyIndex) {
        if (keyIndex < 0 || keyIndex >= KEY_COUNT) return '?';
        return KEY_LETTERS[keyIndex];
    }

    public static String keyName(int keyIndex) {
        if (keyIndex < 0 || keyIndex >= KEY_COUNT) return "?";
        return KEY_NAMES[keyIndex];
    }

    public static boolean isValidKey(int keyIndex) {
        return keyIndex >= 0 && keyIndex < KEY_COUNT;
    }
}
