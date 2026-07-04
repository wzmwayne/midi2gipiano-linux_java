package midi2gipiano;

public class NoteMapper {

    public static final int KEY_COUNT = 21;

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
    private int transpose = 0;

    public NoteMapper() {}

    public NoteMapper(int baseOctave, int transpose) {
        this.baseOctave = baseOctave;
        this.transpose = transpose;
    }

    public int map(int midiNoteNumber) {
        int adjusted = midiNoteNumber + transpose;
        int octave = adjusted / 12 - 1;
        int pc = ((adjusted % 12) + 12) % 12;
        int degree = PITCH_TO_DEGREE[pc];
        int row = ((octave - baseOctave) % 3 + 3) % 3;
        return row * 7 + degree;
    }

    public int getBaseOctave() {
        return baseOctave;
    }

    public void setBaseOctave(int baseOctave) {
        this.baseOctave = baseOctave;
    }

    public int getTranspose() {
        return transpose;
    }

    public void setTranspose(int transpose) {
        this.transpose = transpose;
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
