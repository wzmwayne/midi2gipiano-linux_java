package midi2gipiano;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class HotkeyBinding {
    private static final Path CONFIG_PATH = Paths.get(
        System.getProperty("user.home"), ".config", "midi2gipiano", "hotkey.properties");

    public int modMask;
    public int triggerKey;
    public int evdevMod1;
    public int evdevMod2;
    public int evdevTrigger;
    public String displayName;

    public HotkeyBinding() {
        modMask = 2;
        triggerKey = 61;
        evdevMod1 = 29;
        evdevMod2 = 97;
        evdevTrigger = 13;
        displayName = "Ctrl+=";
    }

    public HotkeyBinding(int modMask, int triggerKey,
                         int evdevMod1, int evdevMod2, int evdevTrigger,
                         String displayName) {
        this.modMask = modMask;
        this.triggerKey = triggerKey;
        this.evdevMod1 = evdevMod1;
        this.evdevMod2 = evdevMod2;
        this.evdevTrigger = evdevTrigger;
        this.displayName = displayName;
    }

    public static HotkeyBinding load() {
        HotkeyBinding b = new HotkeyBinding();
        try {
            if (Files.exists(CONFIG_PATH)) {
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                    p.load(in);
                }
                b.modMask = Integer.parseInt(p.getProperty("modMask", "2"));
                b.triggerKey = Integer.parseInt(p.getProperty("triggerKey", "61"));
                b.evdevMod1 = Integer.parseInt(p.getProperty("evdevMod1", "29"));
                b.evdevMod2 = Integer.parseInt(p.getProperty("evdevMod2", "97"));
                b.evdevTrigger = Integer.parseInt(p.getProperty("evdevTrigger", "13"));
                b.displayName = p.getProperty("displayName", "Ctrl+=");
            }
        } catch (Exception e) {
            System.err.println("hotkey: load failed: " + e.getMessage());
        }
        return b;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Properties p = new Properties();
            p.setProperty("modMask", String.valueOf(modMask));
            p.setProperty("triggerKey", String.valueOf(triggerKey));
            p.setProperty("evdevMod1", String.valueOf(evdevMod1));
            p.setProperty("evdevMod2", String.valueOf(evdevMod2));
            p.setProperty("evdevTrigger", String.valueOf(evdevTrigger));
            p.setProperty("displayName", displayName);
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                p.store(out, "midi2gipiano hotkey config");
            }
        } catch (Exception e) {
            System.err.println("hotkey: save failed: " + e.getMessage());
        }
    }
}
