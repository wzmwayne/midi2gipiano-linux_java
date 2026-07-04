package midi2gipiano;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class GlobalHotkeyManager implements NativeKeyListener {

    private final Runnable onTogglePlayPause;
    private final boolean wayland;
    private EvdevHotkeyThread evdevThread;
    private boolean registered = false;
    private String statusMessage;

    private HotkeyBinding binding;

    public GlobalHotkeyManager(Runnable onTogglePlayPause, HotkeyBinding binding) {
        this.onTogglePlayPause = onTogglePlayPause;
        this.binding = binding;
        this.wayland = "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));
    }

    public void register() {
        if (wayland) {
            registerEvdev();
        } else {
            registerJNativeHook();
        }
    }

    private void registerJNativeHook() {
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            registered = true;
            updateStatusMessage();
        } catch (NativeHookException e) {
            System.err.println("JNativeHook \u6CE8\u518C\u5931\u8D25: " + e.getMessage());
            statusMessage = "\u70ED\u952E: \u6CE8\u518C\u5931\u8D25 - " + e.getMessage();
        }
    }

    private void registerEvdev() {
        if (evdevThread != null) {
            evdevThread.stopThread();
            evdevThread = null;
        }
        evdevThread = new EvdevHotkeyThread(onTogglePlayPause, binding);
        if (evdevThread.findKeyboardDevice()) {
            evdevThread.start();
            registered = true;
            updateStatusMessage();
        } else {
            statusMessage = "\u70ED\u952E: \u4E0D\u53EF\u7528 (\u65E0\u6743\u9650\u8BFB\u53D6\u952E\u76D8\u8BBE\u5907)";
        }
    }

    private void updateStatusMessage() {
        if (wayland) {
            statusMessage = "\u70ED\u952E: " + binding.displayName + " (evdev)";
        } else {
            statusMessage = "\u70ED\u952E: " + binding.displayName + " (\u7CFB\u7EDF\u7EA7)";
        }
    }

    /** Update binding at runtime and re-register the backend. */
    public void rebind(HotkeyBinding newBinding) {
        this.binding = newBinding;
        unregister();
        registered = false;
        register();
    }

    /** Enter capture mode on Wayland — returns the captured binding or null on timeout/error. */
    public HotkeyBinding captureFromEvdev() {
        return evdevThread != null ? evdevThread.capture() : null;
    }

    /** Enter capture mode on X11/Windows via JNativeHook. */
    public HotkeyBinding captureFromJNative() {
        try {
            GlobalScreen.registerNativeHook();
            final int[] capturedKey = {-1};
            final int[] capturedMods = {-1};
            final Object lock = new Object();

            NativeKeyListener listener = new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    synchronized (lock) {
                        if (capturedKey[0] >= 0) return;
                        capturedKey[0] = e.getKeyCode();
                        capturedMods[0] = e.getModifiers();
                        lock.notifyAll();
                    }
                }
                @Override public void nativeKeyReleased(NativeKeyEvent e) {}
                @Override public void nativeKeyTyped(NativeKeyEvent e) {}
            };

            GlobalScreen.addNativeKeyListener(listener);
            synchronized (lock) {
                lock.wait(5000);
            }
            GlobalScreen.removeNativeKeyListener(listener);
            GlobalScreen.unregisterNativeHook();

            if (capturedKey[0] < 0) return null;

            int mm = 0;
            int em1 = -1, em2 = -1;
            int mods = capturedMods[0];
            if ((mods & NativeKeyEvent.CTRL_MASK) != 0) { mm |= NativeKeyEvent.CTRL_MASK; em1 = 29; em2 = 97; }
            if ((mods & NativeKeyEvent.SHIFT_MASK) != 0) { mm |= NativeKeyEvent.SHIFT_MASK; if (em1 < 0) em1 = 42; else em2 = 42; }
            if ((mods & NativeKeyEvent.ALT_MASK) != 0) { mm |= NativeKeyEvent.ALT_MASK; if (em1 < 0) em1 = 56; else em2 = 56; }

            String name = buildDisplayName(mods, ecodesToDisplay(capturedKey[0]));
            int et = vcToEvdev(capturedKey[0]);
            return new HotkeyBinding(mm, capturedKey[0], em1, em2, et, name);
        } catch (Exception e) {
            System.err.println("capture failed: " + e.getMessage());
            return null;
        }
    }

    /** Build display name like "Ctrl+Alt+X" */
    private static String buildDisplayName(int mods, String keyName) {
        StringBuilder sb = new StringBuilder();
        if ((mods & NativeKeyEvent.CTRL_MASK) != 0) sb.append("Ctrl+");
        if ((mods & NativeKeyEvent.SHIFT_MASK) != 0) sb.append("Shift+");
        if ((mods & NativeKeyEvent.ALT_MASK) != 0) sb.append("Alt+");
        if ((mods & NativeKeyEvent.META_MASK) != 0) sb.append("Meta+");
        sb.append(keyName);
        return sb.toString();
    }

    private static String ecodesToDisplay(int vc) {
        if (vc >= NativeKeyEvent.VC_A && vc <= NativeKeyEvent.VC_Z) {
            return String.valueOf((char) ('A' + (vc - NativeKeyEvent.VC_A)));
        }
        if (vc >= NativeKeyEvent.VC_1 && vc <= NativeKeyEvent.VC_9) {
            return String.valueOf((char) ('1' + (vc - NativeKeyEvent.VC_1)));
        }
        if (vc == NativeKeyEvent.VC_0) return "0";
        if (vc == NativeKeyEvent.VC_EQUALS) return "=";
        if (vc == NativeKeyEvent.VC_MINUS) return "-";
        if (vc == NativeKeyEvent.VC_SPACE) return "Space";
        if (vc >= NativeKeyEvent.VC_F1 && vc <= NativeKeyEvent.VC_F24) {
            return "F" + (1 + (vc - NativeKeyEvent.VC_F1));
        }
        if (vc == NativeKeyEvent.VC_BACKSPACE) return "Backspace";
        if (vc == NativeKeyEvent.VC_ENTER) return "Enter";
        if (vc == NativeKeyEvent.VC_TAB) return "Tab";
        if (vc == NativeKeyEvent.VC_ESCAPE) return "Esc";
        return "#" + vc;
    }

    /** Map JNativeHook VC code → evdev keycode */
    private static int vcToEvdev(int vc) {
        // Letters A-Z
        if (vc >= NativeKeyEvent.VC_A && vc <= NativeKeyEvent.VC_Z) {
            return 30 + (vc - NativeKeyEvent.VC_A);
        }
        // Digits 1-9
        if (vc >= NativeKeyEvent.VC_1 && vc <= NativeKeyEvent.VC_9) {
            return 2 + (vc - NativeKeyEvent.VC_1);
        }
        if (vc == NativeKeyEvent.VC_0) return 11;
        if (vc == NativeKeyEvent.VC_EQUALS) return 13;
        if (vc == NativeKeyEvent.VC_MINUS) return 12;
        if (vc == NativeKeyEvent.VC_BACKQUOTE) return 41;
        if (vc == NativeKeyEvent.VC_SEMICOLON) return 39;
        if (vc == NativeKeyEvent.VC_QUOTE) return 40;
        if (vc == NativeKeyEvent.VC_COMMA) return 51;
        if (vc == NativeKeyEvent.VC_PERIOD) return 52;
        if (vc == NativeKeyEvent.VC_SLASH) return 53;
        if (vc == NativeKeyEvent.VC_BACK_SLASH) return 43;
        if (vc == NativeKeyEvent.VC_SPACE) return 57;
        if (vc == NativeKeyEvent.VC_ENTER) return 28;
        if (vc == NativeKeyEvent.VC_TAB) return 15;
        if (vc == NativeKeyEvent.VC_BACKSPACE) return 14;
        if (vc == NativeKeyEvent.VC_ESCAPE) return 1;
        if (vc >= NativeKeyEvent.VC_F1 && vc <= NativeKeyEvent.VC_F24) {
            return 59 + (vc - NativeKeyEvent.VC_F1);
        }
        return -1;
    }

    public void unregister() {
        if (evdevThread != null) {
            evdevThread.stopThread();
            evdevThread = null;
        }
        if (!wayland && registered) {
            try {
                GlobalScreen.removeNativeKeyListener(this);
                GlobalScreen.unregisterNativeHook();
            } catch (NativeHookException e) {
                System.err.println("\u6CE8\u9500\u70ED\u952E\u5931\u8D25: " + e.getMessage());
            }
        }
        registered = false;
    }

    public boolean isRegistered() {
        return registered;
    }

    public String getStatus() {
        return statusMessage != null ? statusMessage : "\u70ED\u952E: \u672A\u6CE8\u518C";
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (e.getKeyCode() == binding.triggerKey) {
            int mods = e.getModifiers();
            if ((mods & binding.modMask) == binding.modMask) {
                SwingUtilities.invokeLater(onTogglePlayPause);
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {}

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {}

    // ── evdev hotkey thread (pure Java, Wayland) ──

    private static class EvdevHotkeyThread extends Thread {
        private static final int EV_KEY = 0x01;
        private static final int KEY_LEFTCTRL  = 29;
        private static final int KEY_RIGHTCTRL = 97;
        private static final int KEY_LEFTSHIFT  = 42;
        private static final int KEY_RIGHTSHIFT = 54;
        private static final int KEY_LEFTALT    = 56;
        private static final int KEY_RIGHTALT   = 100;

        private final Runnable onTrigger;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private String eventPath;
        private final HotkeyBinding binding;

        // Capture mode
        private volatile boolean captureMode = false;
        private volatile int cMod1 = -1, cMod2 = -1, cTrigger = -1;
        private final Object captureLock = new Object();

        EvdevHotkeyThread(Runnable onTrigger, HotkeyBinding binding) {
            super("evdev-hotkey");
            this.onTrigger = onTrigger;
            this.binding = binding;
            setDaemon(true);
        }

        boolean findKeyboardDevice() {
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/bus/input/devices"))) {
                String line;
                boolean isKeyboard = false;
                String handlers = null;

                while ((line = br.readLine()) != null) {
                    if (line.startsWith("N: Name=")) {
                        String name = line.substring(8).replace("\"", "");
                        isKeyboard = name.toLowerCase().contains("keyboard");
                    } else if (line.startsWith("H: Handlers=")) {
                        handlers = line;
                    } else if (line.startsWith("B: EV=") && isKeyboard && handlers != null) {
                        for (String part : handlers.split("\\s+")) {
                            if (part.startsWith("event")) {
                                eventPath = "/dev/input/" + part;
                                if (!new File(eventPath).canRead()) {
                                    System.err.println("evdev: " + eventPath + " \u4E0D\u53EF\u8BFB");
                                    return false;
                                }
                                return true;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("evdev: read /proc/bus/input/devices failed: " + e.getMessage());
            }
            return false;
        }

        void stopThread() {
            running.set(false);
            interrupt();
        }

        /** Capture the next hotkey combo, blocks until timeout (5s). Returns null on failure. */
        HotkeyBinding capture() {
            cMod1 = -1;
            cMod2 = -1;
            cTrigger = -1;
            captureMode = true;

            synchronized (captureLock) {
                try {
                    captureLock.wait(7000);
                } catch (InterruptedException e) {}
            }
            captureMode = false;
            if (cTrigger < 0) return null;

            // Build JNativeHook mask and VC code
            int mm = 0;
            if (cMod1 == KEY_LEFTCTRL || cMod1 == KEY_RIGHTCTRL || cMod2 == KEY_LEFTCTRL || cMod2 == KEY_RIGHTCTRL)
                mm |= NativeKeyEvent.CTRL_MASK;
            if (cMod1 == KEY_LEFTSHIFT || cMod1 == KEY_RIGHTSHIFT || cMod2 == KEY_LEFTSHIFT || cMod2 == KEY_RIGHTSHIFT)
                mm |= NativeKeyEvent.SHIFT_MASK;
            if (cMod1 == KEY_LEFTALT || cMod1 == KEY_RIGHTALT || cMod2 == KEY_LEFTALT || cMod2 == KEY_RIGHTALT)
                mm |= NativeKeyEvent.ALT_MASK;

            int vc = evdevToVc(cTrigger);
            String triggerName = ecodesToDisplay(vc >= 0 ? vc : 0);
            String name = buildDisplayName(mm, triggerName);

            return new HotkeyBinding(mm, vc, cMod1, cMod2, cTrigger, name);
        }

        private static int evdevToVc(int ev) {
            if (ev >= 30 && ev <= 55) return NativeKeyEvent.VC_A + (ev - 30);
            if (ev >= 2 && ev <= 10) return NativeKeyEvent.VC_1 + (ev - 2);
            if (ev == 11) return NativeKeyEvent.VC_0;
            if (ev == 13) return NativeKeyEvent.VC_EQUALS;
            if (ev == 12) return NativeKeyEvent.VC_MINUS;
            if (ev == 57) return NativeKeyEvent.VC_SPACE;
            if (ev == 28) return NativeKeyEvent.VC_ENTER;
            if (ev == 15) return NativeKeyEvent.VC_TAB;
            if (ev == 14) return NativeKeyEvent.VC_BACKSPACE;
            if (ev == 1) return NativeKeyEvent.VC_ESCAPE;
            if (ev >= 59 && ev <= 82) return NativeKeyEvent.VC_F1 + (ev - 59);
            return -1;
        }

        @Override
        public void run() {
            if (eventPath == null) return;

            try (DataInputStream dis = new DataInputStream(new FileInputStream(eventPath))) {
                byte[] buf = new byte[24];
                boolean ctrlDown = false;

                while (running.get()) {
                    dis.readFully(buf);

                    ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
                    bb.getLong();
                    bb.getLong();
                    short type = bb.getShort();
                    short code = bb.getShort();
                    int value = bb.getInt();

                    if (type != EV_KEY) continue;

                    if (captureMode) {
                        handleCapture(code & 0xffff, value);
                    } else {
                        handleHotkey(code & 0xffff, value, ctrlDown);
                        // Track ctrl for hotkey detection
                        int c = code & 0xffff;
                        if (c == KEY_LEFTCTRL || c == KEY_RIGHTCTRL) {
                            ctrlDown = value != 0;
                        }
                    }
                }
            } catch (EOFException e) {
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("evdev: read error: " + e.getMessage());
                }
            }
        }

        private void handleCapture(int code, int value) {
            if (code == KEY_LEFTCTRL || code == KEY_RIGHTCTRL ||
                code == KEY_LEFTSHIFT || code == KEY_RIGHTSHIFT ||
                code == KEY_LEFTALT || code == KEY_RIGHTALT) {
                if (value != 0) {
                    if (cMod1 < 0) cMod1 = code;
                    else if (cMod2 < 0 && code != cMod1) cMod2 = code;
                }
            } else if (value == 0) { // non-modifier released
                if (cTrigger < 0) {
                    cTrigger = code;
                    synchronized (captureLock) {
                        captureLock.notifyAll();
                    }
                }
            }
        }

        private void handleHotkey(int code, int value, boolean ctrlDown) {
            int c = (int) code;
            if (c == binding.evdevMod1 || c == binding.evdevMod2) {
                // We track ctrlDown in the main loop
            } else if (c == binding.evdevTrigger && value == 0 && ctrlDown) {
                SwingUtilities.invokeLater(onTrigger);
            }
        }
    }
}
