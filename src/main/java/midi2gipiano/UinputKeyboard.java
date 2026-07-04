package midi2gipiano;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Pure Java driver-level virtual keyboard via /dev/uinput.
 * Creates a kernel input device identical to a physical keyboard.
 * Requires: user in the "input" group (for /dev/uinput access).
 *
 * IMPORTANT: ioctl's request param is unsigned long (64-bit on x86_64).
 * JNA interface MUST use long, not int, to avoid garbage in upper 32 bits.
 */
public class UinputKeyboard {

    public interface LibC extends Library {
        int open(String path, int flags);
        int close(int fd);
        int ioctl(int fd, long request, long value);
        int ioctl(int fd, long request, Pointer ptr);
        int write(int fd, Pointer buf, int len);
    }

    private static final int O_WRONLY   = 1;
    private static final int O_NONBLOCK = 2048;

    // All ioctl request numbers (fits in 32 bits, but kernel reads 64-bit)
    private static final long UI_SET_EVBIT   = 0x40045564L;
    private static final long UI_SET_KEYBIT  = 0x40045565L;
    private static final long UI_DEV_CREATE  = 0x5501L;
    private static final long UI_DEV_DESTROY = 0x5502L;
    private static final long UI_DEV_SETUP   = 0x405c5503L;

    private static final int EV_KEY = 0x01;
    private static final int EV_SYN = 0x00;

    private static final int[] LYRE_KEY_CODES = {
        16, 17, 18, 19, 20, 21, 22,
        30, 31, 32, 33, 34, 35, 36,
        44, 45, 46, 47, 48, 49, 50
    };

    private final LibC libc;
    private int fd = -1;

    public UinputKeyboard() {
        libc = Native.load("c", LibC.class);
    }

    public synchronized boolean init() {
        if (fd >= 0) return true;

        fd = libc.open("/dev/uinput", O_WRONLY | O_NONBLOCK);
        if (fd < 0) {
            System.err.println("uinput: open /dev/uinput failed (not in input group?)");
            return false;
        }

        if (libc.ioctl(fd, UI_SET_EVBIT, (long) EV_KEY) < 0
                || libc.ioctl(fd, UI_SET_EVBIT, (long) EV_SYN) < 0) {
            System.err.println("uinput: UI_SET_EVBIT failed (errno=" + Native.getLastError() + ")");
            destroy();
            return false;
        }

        for (int code : LYRE_KEY_CODES) {
            if (libc.ioctl(fd, UI_SET_KEYBIT, (long) code) < 0) {
                System.err.println("uinput: UI_SET_KEYBIT " + code + " failed");
                destroy();
                return false;
            }
        }

        // Build uinput_setup struct (92 bytes) — MUST zero-fill entire buffer
        // to avoid garbage in ff_effects_max and name padding
        Memory setup = new Memory(92);
        for (int i = 0; i < 92; i++) setup.setByte(i, (byte) 0);
        setup.setShort(0, (short) 0x0003);   // id.bustype = BUS_USB
        setup.setShort(2, (short) 0x0001);   // id.vendor
        setup.setShort(4, (short) 0x0001);   // id.product
        setup.setShort(6, (short) 0x0100);   // id.version
        byte[] name = "midi2gipiano Virtual Keyboard\0".getBytes();
        int maxLen = Math.min(name.length, 80);
        for (int i = 0; i < maxLen; i++) setup.setByte(8 + i, name[i]);

        if (libc.ioctl(fd, UI_DEV_SETUP, setup) < 0) {
            System.err.println("uinput: UI_DEV_SETUP failed (errno=" + Native.getLastError() + ")");
            destroy();
            return false;
        }

        if (libc.ioctl(fd, UI_DEV_CREATE, 0L) < 0) {
            System.err.println("uinput: UI_DEV_CREATE failed (errno=" + Native.getLastError() + ")");
            destroy();
            return false;
        }

        return true;
    }

    public synchronized void pressKey(int linuxKeyCode) {
        writeEvent(EV_KEY, linuxKeyCode, 1);
        writeEvent(EV_SYN, 0, 0);
    }

    public synchronized void releaseKey(int linuxKeyCode) {
        writeEvent(EV_KEY, linuxKeyCode, 0);
        writeEvent(EV_SYN, 0, 0);
    }

    private void writeEvent(int type, int code, int value) {
        if (fd < 0) return;
        Memory ev = new Memory(24);
        ev.setLong(0, 0);
        ev.setLong(8, 0);
        ev.setShort(16, (short) type);
        ev.setShort(18, (short) code);
        ev.setInt(20, value);
        libc.write(fd, ev, 24);
    }

    public synchronized void destroy() {
        if (fd >= 0) {
            libc.ioctl(fd, UI_DEV_DESTROY, 0L);
            libc.close(fd);
            fd = -1;
        }
    }

    public boolean isActive() {
        return fd >= 0;
    }
}
