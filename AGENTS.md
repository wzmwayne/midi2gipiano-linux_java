# AGENTS.md — midi2gipiano

MIDI → Genshin Impact Windsong Lyre auto-player. Java Swing desktop app.

## Project location

`/home/wayne/桌面/program/java/midi2gipiano` (symlink → `/media/wayne/Elements/Users/Wayne/Documents/program/java/midi2gipiano`)

## Build tools

All at `/home/wayne/桌面/program/jdk-build/`:
- **JDK 21**: `jdk-21-linux/bin/java`
- **Gradle**: `gradle/bin/gradle`

System JDK is **25** (incompatible with Gradle's `jar` task — class file major version 69). Use JDK 21 for Gradle builds:

```bash
JAVA_HOME=/home/wayne/桌面/program/jdk-build/jdk-21-linux \
  /home/wayne/桌面/program/jdk-build/gradle/bin/gradle jar
```

## Manual rebuild (when Gradle jar task fails)

```bash
WORKDIR="/home/wayne/桌面/program/java/midi2gipiano"
JNA_JAR="/home/wayne/.gradle/caches/modules-2/files-2.1/net.java.dev.jna/jna/5.10.0/.../jna-5.10.0.jar"
JNH_JAR="/home/wayne/.gradle/caches/modules-2/files-2.1/com.github.kwhat/jnativehook/2.2.2/.../jnativehook-2.2.2.jar"
CP="$JNH_JAR:$JNA_JAR:$WORKDIR/build/classes/java/main"

javac -cp "$CP" -d "$WORKDIR/build/classes/java/main" src/main/java/midi2gipiano/*.java src/main/java/midi2gipiano/model/*.java

# Build fat JAR from existing JAR (extract + replace classes + repack)
# Or fresh: extract JNA+JNH jars, add compiled classes, write manifest
cd /tmp/jarfix && jar xf "$JNA_JAR" && jar xf "$JNH_JAR"
cp $WORKDIR/build/classes/java/main/midi2gipiano/*.class midi2gipiano/
cp $WORKDIR/build/classes/java/main/midi2gipiano/model/*.class midi2gipiano/model/
rm -f META-INF/MANIFEST.MF
printf 'Manifest-Version: 1.0\nMain-Class: midi2gipiano.App\n\n' > META-INF/MANIFEST.MF
jar cfm "$JAR" META-INF/MANIFEST.MF *
```

## Run

```bash
java --enable-native-access=ALL-UNNAMED -jar build/libs/midi2gipiano-1.0.0.jar
```

## Key packages

| File | Role |
|------|------|
| `App.java` | Entrypoint (main class) |
| `MainWindow.java` | Swing UI, hotkey capture dialog |
| `MidiParser.java` | MIDI file → `ParsedMidi` (PPQ only) |
| `NoteMapper.java` | MIDI note number → 21 key indices (Q–M three rows) |
| `PlaybackEngine.java` | Scheduled playback with pause/resume |
| `KeyPressSimulator.java` | Cross-platform key injection, auto-backend detect |
| `GlobalHotkeyManager.java` | Global hotkey via JNativeHook (X11/Win) or evdev (Wayland) |
| `HotkeyBinding.java` | Config holder, persist to `~/.config/midi2gipiano/hotkey.properties` |
| `UinputKeyboard.java` | JNA + `/dev/uinput` virtual keyboard (Linux driver-level) |
| `Win32Keyboard.java` | JNA + `user32.SendInput` (Windows) |

## Key backend detection (Linux Wayland)

`UINPUT` > `WTYPE` > `YDOTOOL` > `XDOTOOL` > `ROBOT` (window-only)

Uinput requires `input` group membership, zero-initialized `Memory` for ioctl structs, and `long`-typed ioctl request params (64-bit on x86_64).

## Gotchas

- No tests, no CI, no lint
- Only PPQ-format MIDI files supported (check `MidiParser.java:31`)
- Window is always-on-top, non-resizable
- Must click game window for focus after pressing play
- Hotkey config at `~/.config/midi2gipiano/hotkey.properties` (auto-saved after capture)
- `WindsongLyre-Sim/` is an independent web project, unrelated to Java build
- `build/libs/JNativeHook.x86_64.dll` is stale artifact, ignore
- JNA 5.10.0 in cache (build.gradle says 5.14.0 but may not resolve — use cached version)
