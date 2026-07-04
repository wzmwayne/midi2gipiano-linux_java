package midi2gipiano;

import midi2gipiano.MidiParser.ParsedMidi;
import midi2gipiano.PlaybackEngine.State;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class MainWindow extends JFrame {

    private final MidiParser parser = new MidiParser();
    private final NoteMapper mapper = new NoteMapper();
    private KeyPressSimulator simulator;
    private PlaybackEngine engine;
    private GlobalHotkeyManager hotkeyManager;
    private HotkeyBinding hotkeyBinding;

    private JLabel statusLabel;
    private JLabel fileLabel;
    private JLabel noteLabel;
    private JLabel timeLabel;
    private JLabel hotkeyLabel;
    private JButton playBtn, pauseBtn, stopBtn, hotkeyBtn;
    private JProgressBar progressBar;
    private JSpinner baseOctaveSpinner;
    private JSpinner transposeSpinner;
    private File currentFile;
    private ParsedMidi parsed;

    private final Timer uiTimer;

    public MainWindow() {
        super("midi2gipiano - MIDI \u2192 \u539F\u795E\u98CE\u7269\u4E4B\u8BD7\u7434 \u81EA\u52A8\u6F14\u594F");
        simulator = new KeyPressSimulator();

        engine = new PlaybackEngine(mapper, simulator);
        setupListeners();

        hotkeyBinding = HotkeyBinding.load();
        hotkeyManager = new GlobalHotkeyManager(() -> {
            if (parsed == null) return;
            if (engine.isPlaying()) {
                pause();
            } else {
                play();
            }
        }, hotkeyBinding);
        hotkeyManager.register();

        initUI();

        statusLabel.setText(" \u72B6\u6001: \u5C31\u7EEA | \u952E\u76D8: " + simulator.backendName());

        String warn = simulator.getWarning();
        if (warn != null) {
            JLabel warnLabel = new JLabel("\u26A0 " + warn, SwingConstants.CENTER);
            warnLabel.setForeground(new Color(0xCC, 0x66, 0x00));
            warnLabel.setFont(warnLabel.getFont().deriveFont(11f));
            add(warnLabel, BorderLayout.NORTH);
        }

        checkWaylandSetup();

        uiTimer = new Timer(50, e -> updateUI());
        uiTimer.start();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                hotkeyManager.unregister();
                simulator.shutdown();
            }
        });
        setSize(520, 400);
        setLocationRelativeTo(null);
        setResizable(false);
        setAlwaysOnTop(true);
    }

    private void checkWaylandSetup() {
        if (!"wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"))) return;

        if (simulator.getBackend() == KeyPressSimulator.Backend.UINPUT) return;

        String diag = simulator.getDiagnostics();
        if (diag != null) {
            Object[] options = {"\u77E5\u9053\u4E86"};
            JOptionPane.showOptionDialog(this,
                    "<html><b>\u6309\u952E\u6A21\u62DF\u540E\u7AEF\u63D0\u793A</b><br><br>"
                    + diag + "<br><br>"
                    + "\u5F53\u524D\u540E\u7AEF: " + simulator.backendName() + "</html>",
                    "\u63D0\u793A",
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, options, options[0]);
        }

        if (!hotkeyManager.isRegistered()) {
            String[] options = {"\u5F88\u6574" + (char)0x2026};
            JOptionPane.showOptionDialog(this,
                    "<html><b>\u5168\u5C40\u70ED\u952E\u672A\u80FD\u6CE8\u518C</b><br><br>"
                    + "Wayland \u4E0B\u9700\u8981\u8BFB\u53D6 /dev/input/event* \u6743\u9650\u624D\u80FD\u4F7F\u7528\u5168\u5C40\u70ED\u952E\u3002<br>"
                    + "\u8BF7\u6267\u884C\uFF1A<br><br>"
                    + "<b>sudo gpasswd -a $USER input</b><br>"
                    + "\u7136\u540E\u767B\u51FA\u91CD\u65B0\u767B\u5F55\u3002<br><br>"
                    + "\u6216\u8005\u4F7F\u7528\u7A97\u53E3\u5185\u5FEB\u6377\u952E (\u4EC5\u5F53Java\u7A97\u53E3\u805A\u7126\u65F6\u751F\u6548)\u3002</html>",
                    "\u70ED\u952E\u8BBE\u7F6E",
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, options, options[0]);
        }
    }

    private void setupListeners() {
        engine.setListener(new PlaybackEngine.Listener() {
            @Override
            public void onNoteChanged(int midiNote, int keyIndex, char keyLetter) {}

            @Override
            public void onStateChanged(State state) {
                SwingUtilities.invokeLater(() -> updateButtonStates());
            }

            @Override
            public void onFinished() {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("\u64AD\u653E\u5B8C\u6210");
                    updateButtonStates();
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(MainWindow.this, message, "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JPanel topPanel = createFilePanel();
        JPanel controlPanel = createControlPanel();
        JPanel infoPanel = createInfoPanel();
        JPanel settingsPanel = createSettingsPanel();

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.gridx = 0;

        gbc.gridy = 0;
        center.add(topPanel, gbc);
        gbc.gridy = 1;
        center.add(controlPanel, gbc);
        gbc.gridy = 2;
        center.add(infoPanel, gbc);
        gbc.gridy = 3;
        center.add(settingsPanel, gbc);

        add(center, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JLabel tipLabel = new JLabel(
                "\u64CD\u4F5C\u63D0\u793A: \u70B9\u51FB\u201C\u64AD\u653E\u201D\u540E\u7ACB\u5373\u70B9\u51FB\u6E38\u620F\u7A97\u53E3\u4EE5\u83B7\u5F97\u7126\u70B9",
                SwingConstants.CENTER);
        tipLabel.setFont(tipLabel.getFont().deriveFont(11f));
        tipLabel.setForeground(Color.GRAY);

        hotkeyLabel = new JLabel(hotkeyManager.getStatus(), SwingConstants.CENTER);
        hotkeyLabel.setFont(hotkeyLabel.getFont().deriveFont(10f));
        hotkeyLabel.setForeground(hotkeyManager.isRegistered() ? new Color(0x33, 0x66, 0x33) : Color.GRAY);

        bottomPanel.add(tipLabel, BorderLayout.NORTH);
        bottomPanel.add(hotkeyLabel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private JPanel createFilePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(BorderFactory.createTitledBorder("MIDI \u6587\u4EF6"));

        fileLabel = new JLabel(" \u672A\u9009\u62E9\u6587\u4EF6");
        fileLabel.setPreferredSize(new Dimension(300, 24));

        JButton chooseBtn = new JButton("\u9009\u62E9");
        chooseBtn.addActionListener(e -> chooseFile());

        statusLabel = new JLabel(" \u72B6\u6001: \u5C31\u7EEA");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        JPanel left = new JPanel(new BorderLayout());
        left.add(fileLabel, BorderLayout.NORTH);
        left.add(statusLabel, BorderLayout.SOUTH);

        panel.add(left, BorderLayout.CENTER);
        panel.add(chooseBtn, BorderLayout.EAST);
        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        panel.setBorder(BorderFactory.createTitledBorder("\u64AD\u653E\u63A7\u5236"));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));

        playBtn = new JButton("\u25B6 \u64AD\u653E");
        pauseBtn = new JButton("\u23F8 \u6682\u505C");
        stopBtn = new JButton("\u25A0 \u505C\u6B62");

        playBtn.addActionListener(e -> play());
        pauseBtn.addActionListener(e -> pause());
        stopBtn.addActionListener(e -> stop());

        btnPanel.add(playBtn);
        btnPanel.add(pauseBtn);
        btnPanel.add(stopBtn);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        panel.add(btnPanel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 4, 2));
        panel.setBorder(BorderFactory.createTitledBorder("\u5F53\u524D\u97F3\u7B26"));

        noteLabel = new JLabel(" MIDI: --  \u2192  \u6309\u952E: --  (--)", SwingConstants.CENTER);
        noteLabel.setFont(noteLabel.getFont().deriveFont(14f));

        timeLabel = new JLabel(" \u5DF2\u64AD: 00:00.0 / 00:00.0", SwingConstants.CENTER);

        panel.add(noteLabel);
        panel.add(timeLabel);
        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        panel.setBorder(BorderFactory.createTitledBorder("\u8BBE\u7F6E"));

        baseOctaveSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 9, 1));
        baseOctaveSpinner.setPreferredSize(new Dimension(60, 24));
        transposeSpinner = new JSpinner(new SpinnerNumberModel(0, -24, 24, 1));
        transposeSpinner.setPreferredSize(new Dimension(60, 24));

        baseOctaveSpinner.addChangeListener(e -> {
            mapper.setBaseOctave((Integer) baseOctaveSpinner.getValue());
        });
        transposeSpinner.addChangeListener(e -> {
            mapper.setTranspose((Integer) transposeSpinner.getValue());
        });

        hotkeyBtn = new JButton("\u8BBE\u7F6E\u5FEB\u6377\u952E");
        hotkeyBtn.addActionListener(e -> captureHotkey());

        panel.add(new JLabel("\u57FA\u7840\u516B\u5EA6:"));
        panel.add(baseOctaveSpinner);
        panel.add(new JLabel("\u79FB\u8C03(\u534A\u97F3):"));
        panel.add(transposeSpinner);
        panel.add(hotkeyBtn);
        return panel;
    }

    private void captureHotkey() {
        JDialog dialog = new JDialog(this, "\u8BBE\u7F6E\u5168\u5C40\u5FEB\u6377\u952E", true);
        dialog.setLayout(new BorderLayout());
        JLabel msg = new JLabel("\u57287\u79D2\u5185\u6309\u4E0B\u60A8\u8981\u8BBE\u7F6E\u7684\u5FEB\u6377\u952E\u7EC4\u5408...", SwingConstants.CENTER);
        msg.setFont(msg.getFont().deriveFont(14f));
        dialog.add(msg, BorderLayout.CENTER);
        dialog.setSize(360, 120);
        dialog.setLocationRelativeTo(this);

        // Capture in background thread
        SwingWorker<HotkeyBinding, Void> worker = new SwingWorker<>() {
            @Override
            protected HotkeyBinding doInBackground() {
                boolean wayland = "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));
                if (wayland) {
                    return hotkeyManager.captureFromEvdev();
                } else {
                    return hotkeyManager.captureFromJNative();
                }
            }

            @Override
            protected void done() {
                dialog.dispose();
                try {
                    HotkeyBinding captured = get();
                    if (captured != null) {
                        hotkeyBinding = captured;
                        hotkeyBinding.save();
                        hotkeyManager.rebind(hotkeyBinding);
                        hotkeyLabel.setText(hotkeyManager.getStatus());
                        hotkeyLabel.setForeground(hotkeyManager.isRegistered() ? new Color(0x33, 0x66, 0x33) : Color.GRAY);
                        JOptionPane.showMessageDialog(MainWindow.this,
                                "\u5FEB\u6377\u952E\u5DF2\u66F4\u65B0\u4E3A: " + captured.displayName,
                                "\u6210\u529F", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(MainWindow.this,
                                "\u8D85\u65F6\u6216\u672A\u6355\u83B7\u5230\u6309\u952E\u7EC4\u5408",
                                "\u5931\u8D25", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "\u6355\u83B7\u5931\u8D25: " + e.getMessage(),
                            "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
        dialog.setVisible(true);
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("MIDI \u6587\u4EF6 (*.mid, *.midi)", "mid", "midi"));
        if (currentFile != null) {
            fc.setCurrentDirectory(currentFile.getParentFile());
        }

        int result = fc.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.exists()) return;

        loadFile(file);
    }

    private void loadFile(File file) {
        statusLabel.setText(" \u72B6\u6001: \u6B63\u5728\u89E3\u6790...");
        currentFile = file;
        fileLabel.setText(" " + file.getName());

        SwingWorker<ParsedMidi, Void> worker = new SwingWorker<>() {
            @Override
            protected ParsedMidi doInBackground() throws Exception {
                return parser.parse(file);
            }

            @Override
            protected void done() {
                try {
                    parsed = get();
                    engine.setNotes(parsed.notes, parsed.durationMicros);
                    statusLabel.setText(String.format(
                            " \u72B6\u6001: \u5DF2\u52A0\u8F7D | %d \u4E2A\u8F68\u9053 | %d \u4E2A\u97F3\u7B26 | \u65F6\u957F %s",
                            parsed.trackCount, parsed.notes.size(),
                            formatTime(parsed.durationMicros)));
                } catch (Exception e) {
                    statusLabel.setText(" \u72B6\u6001: \u89E3\u6790\u5931\u8D25");
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "\u89E3\u6790 MIDI \u6587\u4EF6\u5931\u8D25:\n" + e.getMessage(),
                            "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void play() {
        if (parsed == null) {
            JOptionPane.showMessageDialog(this, "\u8BF7\u5148\u9009\u62E9 MIDI \u6587\u4EF6", "\u63D0\u793A", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (engine.isPlaying()) return;

        if (engine.isPaused()) {
            engine.play();
        } else {
            engine.setNotes(parsed.notes, parsed.durationMicros);
            engine.play();
        }
        statusLabel.setText(" \u72B6\u6001: \u64AD\u653E\u4E2D");
        updateButtonStates();
    }

    private void pause() {
        engine.pause();
        statusLabel.setText(" \u72B6\u6001: \u5DF2\u6682\u505C");
        updateButtonStates();
    }

    private void stop() {
        engine.stop();
        statusLabel.setText(" \u72B6\u6001: \u5DF2\u505C\u6B62");
        updateButtonStates();
    }

    private void updateUI() {
        if (engine.isPlaying() || engine.isPaused()) {
            long pos = engine.getCurrentPositionMicros();
            long total = engine.getTotalDurationMicros();
            int percent = total > 0 ? (int) (pos * 100 / total) : 0;
            progressBar.setValue(percent);
            progressBar.setString(percent + "%");
            timeLabel.setText(String.format(" \u5DF2\u64AD: %s / %s",
                    formatTime(pos), formatTime(total)));

            int mn = engine.getCurrentMidiNote();
            int ki = engine.getCurrentKeyIndex();
            if (mn >= 0) {
                String noteName = noteName(mn);
                char keyLetter = NoteMapper.keyLetter(ki);
                String keyLabel = NoteMapper.keyName(ki);
                noteLabel.setText(String.format(" %s  \u2192  \u6309\u952E: %s  (%s)",
                        noteName, keyLetter, keyLabel));
            }
        }
    }

    private void updateButtonStates() {
        State s = engine.getState();
        playBtn.setEnabled(s != State.PLAYING && parsed != null);
        pauseBtn.setEnabled(s == State.PLAYING);
        stopBtn.setEnabled(s != State.STOPPED);

        hotkeyLabel.setText(hotkeyManager.getStatus());
        hotkeyLabel.setForeground(hotkeyManager.isRegistered() ? new Color(0x33, 0x66, 0x33) : Color.GRAY);
    }

    private String noteName(int note) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        return names[note % 12] + (note / 12 - 1);
    }

    private String formatTime(long micros) {
        long totalSec = micros / 1_000_000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        long tenths = (micros % 1_000_000) / 100_000;
        return String.format("%02d:%02d.%d", min, sec, tenths);
    }
}
