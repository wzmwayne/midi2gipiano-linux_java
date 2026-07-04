package midi2gipiano;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class KeyPressSimulator {

    public enum Backend { ROBOT, XDOTOOL, YDOTOOL, WTYPE, UINPUT, SENDINPUT, NONE }

    private static final int[] KEY_EVENTS = {
        KeyEvent.VK_Q, KeyEvent.VK_W, KeyEvent.VK_E, KeyEvent.VK_R,
        KeyEvent.VK_T, KeyEvent.VK_Y, KeyEvent.VK_U,
        KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_F,
        KeyEvent.VK_G, KeyEvent.VK_H, KeyEvent.VK_J,
        KeyEvent.VK_Z, KeyEvent.VK_X, KeyEvent.VK_C, KeyEvent.VK_V,
        KeyEvent.VK_B, KeyEvent.VK_N, KeyEvent.VK_M
    };

    private static final String[] KEY_NAMES = {
        "q", "w", "e", "r", "t", "y", "u",
        "a", "s", "d", "f", "g", "h", "j",
        "z", "x", "c", "v", "b", "n", "m"
    };

    private static final String[] YDOTOOL_NAMES = {
        "KEY_Q", "KEY_W", "KEY_E", "KEY_R", "KEY_T", "KEY_Y", "KEY_U",
        "KEY_A", "KEY_S", "KEY_D", "KEY_F", "KEY_G", "KEY_H", "KEY_J",
        "KEY_Z", "KEY_X", "KEY_C", "KEY_V", "KEY_B", "KEY_N", "KEY_M"
    };

    /** uinput key codes (linux/input-event-codes.h) */
    private static final int[] UINPUT_CODES = {
        16, 17, 18, 19, 20, 21, 22,   // Q W E R T Y U
        30, 31, 32, 33, 34, 35, 36,   // A S D F G H J
        44, 45, 46, 47, 48, 49, 50    // Z X C V B N M
    };

    private final Robot robot;
    private final Backend backend;
    private final boolean[] pressed = new boolean[NoteMapper.KEY_COUNT];
    private final String osName;
    private final boolean wayland;
    private String backendDiagnostics;
    private String lastExecError;

    private UinputKeyboard uinputKeyboard;
    private Win32Keyboard win32Keyboard;

    public KeyPressSimulator() {
        osName = System.getProperty("os.name").toLowerCase();
        wayland = "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));

        Robot r = null;
        try {
            r = new Robot();
        } catch (AWTException e) {
            System.err.println("Robot init failed: " + e.getMessage());
        }
        robot = r;

        backend = detectBackend();
    }

    private Backend detectBackend() {
        if (robot == null) {
            backendDiagnostics = "java.awt.Robot \u521D\u59CB\u5316\u5931\u8D25";
            return Backend.NONE;
        }

        // ── Windows: JNA SendInput (driver-level) ──
        if (osName.contains("win")) {
            win32Keyboard = new Win32Keyboard();
            if (win32Keyboard.isAvailable()) {
                backendDiagnostics = null;
                return Backend.SENDINPUT;
            }
            backendDiagnostics = "JNA SendInput \u52A0\u8F7D\u5931\u8D25\uFF0C\u964D\u7EA7\u81F3 Robot";
            return Backend.ROBOT;
        }

        // ── macOS ──
        if (osName.contains("mac")) {
            return Backend.ROBOT;
        }

        // ── X11 ──
        if (!wayland) {
            return Backend.ROBOT;
        }

        // ═════════════════════════════════════════╗
        // ║  Linux Wayland: driver-level uinput    ║
        // ═════════════════════════════════════════╝
        uinputKeyboard = new UinputKeyboard();
        if (uinputKeyboard.init()) {
            backendDiagnostics = null;
            return Backend.UINPUT;
        }

        // ── Fallback: system tools ──
        if (testTool("wtype", "wtype", "-k", "Shift_L")) {
            return Backend.WTYPE;
        }
        if (checkCommand("ydotool") && isDaemonRunning("ydotoold")) {
            if (testTool("ydotool", "ydotool", "key", "KEY_LEFTSHIFT:1", "KEY_LEFTSHIFT:0")) {
                return Backend.YDOTOOL;
            }
            backendDiagnostics = "ydotool \u8FDE\u63A5 ydotoold \u5931\u8D25";
        }
        if (testTool("xdotool", "xdotool", "key", "Shift_L")) {
            return Backend.XDOTOOL;
        }

        backendDiagnostics = "\u65E0\u6CD5\u8DE8\u7A97\u53E3: Wayland \u4E0B Robot \u4EC5\u80FD\u5728\u672C\u7A97\u53E3\u5185\u6A21\u62DF";
        return Backend.ROBOT;
    }

    private static boolean testTool(String label, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            boolean ok = p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0;
            if (!ok && !output.isEmpty()) {
                System.err.println(label + " test: " + output.trim());
            }
            return ok;
        } catch (Exception e) {
            System.err.println(label + " test error: " + e.getMessage());
            return false;
        }
    }

    public Backend getBackend() {
        return backend;
    }

    public String backendName() {
        switch (backend) {
            case ROBOT:
                if (osName.contains("win")) return "java.awt.Robot (Windows)";
                if (osName.contains("mac")) return "java.awt.Robot (macOS)";
                return "java.awt.Robot (" + (wayland ? "Wayland, \u4EC5\u672C\u7A97\u53E3" : "X11") + ")";
            case XDOTOOL:   return "xdotool";
            case YDOTOOL:   return "ydotool";
            case WTYPE:     return "wtype (Wayland)";
            case UINPUT:    return "uinput (\u865A\u62DF\u952E\u76D8\u8BBE\u5907)";
            case SENDINPUT: return "SendInput (\u786C\u4EF6\u7EA7)";
            case NONE:      return "\u65E0\u53EF\u7528\u540E\u7AEF";
        }
        return "?";
    }

    public boolean isAvailable() {
        return backend != Backend.NONE;
    }

    public String getDiagnostics() {
        return backendDiagnostics;
    }

    public String getWarning() {
        if (backend == Backend.UINPUT) return null;
        if (backend == Backend.SENDINPUT) return null;
        if (backend == Backend.ROBOT && wayland) {
            return "Wayland \u4E0B\u65E0\u6CD5\u8DE8\u7A97\u53E3\u6A21\u62DF\u6309\u952E\u3002"
                + "\u5EFA\u8BAE\u6267\u884C: sudo usermod -aG input $USER \u5E76\u91CD\u65B0\u767B\u5F55";
        }
        return null;
    }

    public String getSetupCommand() {
        if (!wayland) return null;
        if (backend == Backend.ROBOT) return "sudo usermod -aG input $USER";
        if (backend == Backend.YDOTOOL) return "sudo ydotoold";
        return null;
    }

    private boolean isDaemonRunning(String name) {
        try {
            Process p = new ProcessBuilder("pgrep", "-x", name)
                    .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void press(int keyIndex) {
        if (keyIndex < 0 || keyIndex >= NoteMapper.KEY_COUNT) return;
        if (pressed[keyIndex]) return;
        pressed[keyIndex] = true;

        switch (backend) {
            case ROBOT:
                robot.keyPress(KEY_EVENTS[keyIndex]);
                break;
            case XDOTOOL:
                exec("xdotool", "keydown", KEY_NAMES[keyIndex]);
                break;
            case YDOTOOL:
                exec("ydotool", "key", YDOTOOL_NAMES[keyIndex] + ":1");
                break;
            case WTYPE:
                exec("wtype", "-P", KEY_NAMES[keyIndex]);
                break;
            case UINPUT:
                uinputKeyboard.pressKey(UINPUT_CODES[keyIndex]);
                break;
            case SENDINPUT:
                win32Keyboard.pressKey(keyIndex);
                break;
        }
    }

    public void release(int keyIndex) {
        if (keyIndex < 0 || keyIndex >= NoteMapper.KEY_COUNT) return;
        if (!pressed[keyIndex]) return;
        pressed[keyIndex] = false;

        switch (backend) {
            case ROBOT:
                robot.keyRelease(KEY_EVENTS[keyIndex]);
                break;
            case XDOTOOL:
                exec("xdotool", "keyup", KEY_NAMES[keyIndex]);
                break;
            case YDOTOOL:
                exec("ydotool", "key", YDOTOOL_NAMES[keyIndex] + ":0");
                break;
            case WTYPE:
                exec("wtype", "-p", KEY_NAMES[keyIndex]);
                break;
            case UINPUT:
                uinputKeyboard.releaseKey(UINPUT_CODES[keyIndex]);
                break;
            case SENDINPUT:
                win32Keyboard.releaseKey(keyIndex);
                break;
        }
    }

    public void releaseAll() {
        List<String> ydotoolArgs = null;

        for (int i = 0; i < NoteMapper.KEY_COUNT; i++) {
            if (pressed[i]) {
                pressed[i] = false;
                switch (backend) {
                    case ROBOT:
                        robot.keyRelease(KEY_EVENTS[i]);
                        break;
                    case XDOTOOL:
                        exec("xdotool", "keyup", KEY_NAMES[i]);
                        break;
                    case YDOTOOL:
                        if (ydotoolArgs == null) {
                            ydotoolArgs = new ArrayList<>();
                            ydotoolArgs.add("ydotool");
                            ydotoolArgs.add("key");
                        }
                        ydotoolArgs.add(YDOTOOL_NAMES[i] + ":0");
                        break;
                    case WTYPE:
                        exec("wtype", "-p", KEY_NAMES[i]);
                        break;
                    case UINPUT:
                        uinputKeyboard.releaseKey(UINPUT_CODES[i]);
                        break;
                    case SENDINPUT:
                        win32Keyboard.releaseKey(i);
                        break;
                }
            }
        }

        if (ydotoolArgs != null && ydotoolArgs.size() > 3) {
            exec(ydotoolArgs.toArray(new String[0]));
        }
    }

    public void shutdown() {
        releaseAll();
        if (uinputKeyboard != null) {
            uinputKeyboard.destroy();
        }
    }

    public String getLastExecError() {
        return lastExecError;
    }

    private void exec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true).start();

            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                lastExecError = "\u8D85\u65F6: " + String.join(" ", cmd);
                System.err.println(lastExecError);
                return;
            }

            String output = new String(p.getInputStream().readAllBytes()).trim();

            if (p.exitValue() != 0) {
                lastExecError = String.join(" ", cmd) + " \u8FD4\u56DE " + p.exitValue()
                        + (!output.isEmpty() ? " - " + output : "");
                System.err.println("exec exit " + p.exitValue() + ": " + lastExecError);
            } else {
                lastExecError = null;
            }
        } catch (Exception e) {
            lastExecError = String.join(" ", cmd) + " - " + e.getMessage();
            System.err.println("exec error: " + lastExecError);
        }
    }

    private static boolean checkCommand(String name) {
        try {
            Process p = new ProcessBuilder("which", name)
                    .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
