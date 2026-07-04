package midi2gipiano;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Windows key injection via JNA + SendInput.
 * Manually builds the INPUT structure in native memory
 * to avoid JNA struct alignment issues.
 *
 * Memory layout (x86_64 / x86):
 *   INPUT {
 *     DWORD type;                        // +0, 4B
 *     padding                            // +4, 0B on x86, 4B on x64
 *     union { KEYBDINPUT ki; ... }       // +KI_OFFSET
 *   }
 *
 *   KEYBDINPUT {
 *     WORD wVk;                          // +0, 2B
 *     WORD wScan;                        // +2, 2B
 *     DWORD dwFlags;                     // +4, 4B
 *     DWORD time;                        // +8, 4B
 *     ULONG_PTR dwExtraInfo;             // +16, POINTER_SIZE (on x64: 8B)
 *   }
 *
 * sizeof(INPUT): 32 on x86_64, 20 on x86
 */
public class Win32Keyboard {

    public interface User32 extends com.sun.jna.Library {
        int SendInput(int nInputs, Pointer pInputs, int cbSize);
    }

    private static final boolean IS_64BIT = Native.POINTER_SIZE == 8;
    private static final int KI_OFFSET = IS_64BIT ? 8 : 4;
    private static final int INPUT_SIZE = IS_64BIT ? 32 : 20;

    private static final int INPUT_KEYBOARD = 1;
    private static final int KEYEVENTF_KEYUP = 0x0002;
    private static final int KEYEVENTF_SCANCODE = 0x0008;

    // Windows scan codes for Q-M row
    private static final int[] SCAN_MAP = {
        0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,  // Q W E R T Y U
        0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24,  // A S D F G H J
        0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32   // Z X C V B N M
    };

    // Virtual-key codes for Q-M row
    private static final int[] VK_MAP = {
        0x51, 0x57, 0x45, 0x52, 0x54, 0x59, 0x55,  // Q W E R T Y U
        0x41, 0x53, 0x44, 0x46, 0x47, 0x48, 0x4A,  // A S D F G H J
        0x5A, 0x58, 0x43, 0x56, 0x42, 0x4E, 0x4D   // Z X C V B N M
    };

    private final User32 user32;
    private boolean useScanCodes = false;

    public Win32Keyboard() {
        User32 u = null;
        try {
            u = Native.load("user32", User32.class);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Win32: user32 load failed: " + e.getMessage());
        }
        user32 = u;
    }

    public boolean isAvailable() { return user32 != null; }

    public void pressKey(int keyIndex) {
        send(keyIndex, true);
    }

    public void releaseKey(int keyIndex) {
        send(keyIndex, false);
    }

    private void send(int keyIndex, boolean down) {
        if (user32 == null) return;

        Memory m = new Memory(INPUT_SIZE);
        m.clear();

        // type = INPUT_KEYBOARD at offset 0
        m.setInt(0, INPUT_KEYBOARD);

        int off = KI_OFFSET;
        if (useScanCodes) {
            m.setShort(off,     (short) 0);                      // wVk = 0
            m.setShort(off + 2, (short) SCAN_MAP[keyIndex]);     // wScan
            m.setInt(off + 4,   KEYEVENTF_SCANCODE
                               | (down ? 0 : KEYEVENTF_KEYUP));  // dwFlags
        } else {
            m.setShort(off,     (short) VK_MAP[keyIndex]);       // wVk
            m.setShort(off + 2, (short) 0);                      // wScan = 0
            m.setInt(off + 4,   down ? 0 : KEYEVENTF_KEYUP);     // dwFlags
        }

        // time = 0, dwExtraInfo = NULL (already zeroed by clear())

        int result = user32.SendInput(1, m, INPUT_SIZE);
        if (result != 1 && !useScanCodes) {
            useScanCodes = true;
            send(keyIndex, down);
        }
    }
}
