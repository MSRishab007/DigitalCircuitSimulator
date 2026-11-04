package simulator.gui;

import simulator.components.Clock;
import simulator.application.SimulationApplicationService;
import simulator.core.DigitalComponent;
import simulator.engine.CircuitEquivalenceChecker;
import simulator.engine.CircuitPorts;
import simulator.engine.TimingAnalysisResult;
import simulator.engine.TruthTable;
import simulator.engine.SimulationContext;
import simulator.engine.SimulationDebugger;
import simulator.io.VcdLogger;
import simulator.parser.NetlistParser;
import simulator.parser.TopologyValidator;
import simulator.parser.SimulationRunner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainView;
import javax.swing.text.Segment;
import javax.swing.text.Utilities;
import javax.swing.text.View;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimulatorDashboard extends JFrame {
    private static final Pattern ERROR_LINE = Pattern.compile("(?i)\\bline (\\d+)\\b");
    private static final int MAX_WIRES_IN_MESSAGE = 30;

    private final JTextArea editorArea;
    private final JTextArea logConsole;
    private final WaveformPanel waveformPanel;
    private final JButton runBtn;
    private final JButton stepBtn;
    private final JButton resetBtn;
    private final JLabel statusLabel;
    private final SimulationApplicationService service =
            new SimulationApplicationService();
    private SimulationContext debugContext;
    private SimulationDebugger debugger;
    private NetlistParser debugParser;
    private VcdLogger debugLogger;
    private String debugWaveformPath;
    private File currentFile;

    private final JLabel fileLabel = Ui.label("Untitled netlist", Theme.MUTED, Theme.ui(Font.PLAIN, 12f));
    private final JLabel activityLabel = Ui.label("Ready", Theme.MUTED, Theme.ui(Font.PLAIN, 12f));
    private final JLabel caretLabel = Ui.label("Ln 1, Col 1", Theme.MUTED, Theme.ui(Font.PLAIN, 12f));
    private final JTextField inputsField = new SlimField("Input_A,Input_B,Input_Cin");
    private final JTextField outputsField = new SlimField("Sum,CarryOut");
    private final JTextArea resultArea = new CodeArea(13f, null,
            "Results from the buttons above appear here.", false);
    private final DefaultTableModel truthModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable truthTableView = new JTable(truthModel);
    private final JLabel truthSummary = Ui.label(" ", Theme.MUTED, Theme.ui(Font.PLAIN, 12f));
    private final CardLayout resultCards = new CardLayout();
    private final JPanel resultHost = new JPanel(resultCards);
    private final ColorButton exportCsvBtn = new ColorButton("Export CSV", Theme.SLATE);
    private final UndoManager undo = new UndoManager();
    private final SchematicPanel schematicPanel = new SchematicPanel();
    private final JSlider timeSlider = new JSlider(0, 1, 0);
    private final JLabel timeLabel = Ui.label(" ", Theme.MUTED, Theme.ui(Font.PLAIN, 12f));
    private final ColorButton previousEdgeBtn = new ColorButton("Previous change", Theme.SLATE);
    private final ColorButton nextEdgeBtn = new ColorButton("Next change", Theme.SLATE);
    private final javax.swing.Timer schematicTimer = new javax.swing.Timer(400, e -> refreshSchematic());
    private JPanel schematicTab;
    private String simulatedNetlist;
    private boolean updatingSlider;
    private final List<JButton> busyButtons = new ArrayList<>();
    private final Map<JButton, Boolean> enabledBeforeBusy = new HashMap<>();
    private TruthTable lastTruthTable;
    private Object errorHighlight;
    private boolean dirty;
    private boolean busy;

    public SimulatorDashboard() {
        Theme.installDefaults();

        setTitle("EDA Digital Circuit Simulator - Dashboard");
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1280, screen.width - 40), Math.min(800, screen.height - 60));
        setMinimumSize(new Dimension(980, 560));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Color.WHITE);

        // --- Toolbar ---
        ColorButton openBtn = new ColorButton("Open", Theme.BLUE);
        ColorButton saveBtn = new ColorButton("Save", Theme.BLUE);
        ColorButton saveAsBtn = new ColorButton("Save as", Theme.BLUE);
        runBtn = new ColorButton("Run simulation", Theme.GREEN);
        stepBtn = new ColorButton("Step event", Theme.TEAL);
        resetBtn = new ColorButton("Reset", Theme.ORANGE);
        statusLabel = Ui.label("No simulation loaded", Theme.MUTED, Theme.ui(Font.PLAIN, 12f));

        String mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() == InputEvent.META_DOWN_MASK
                ? "Cmd" : "Ctrl";
        openBtn.setToolTipText("Open netlist (" + mod + "+O)");
        saveBtn.setToolTipText("Save netlist (" + mod + "+S)");
        saveAsBtn.setToolTipText("Save netlist to a new file (" + mod + "+Shift+S)");
        runBtn.setToolTipText("Run simulation (F5)");
        stepBtn.setToolTipText("Process the next event (F10)");
        resetBtn.setToolTipText("Restart stepping from time 0 (Shift+F5)");

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(Color.WHITE);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(8, 12, 8, 12)));
        toolbar.add(Ui.row(6, openBtn, saveBtn, saveAsBtn, Ui.gap(16), runBtn, stepBtn, resetBtn),
                BorderLayout.WEST);

        // --- Editor Pane (Left) ---
        editorArea = new CodeArea(14f, Colorizers::netlist, null, true);
        editorArea.setTabSize(4);
        editorArea.setText(getDefaultNetlistSample());
        editorArea.setCaretPosition(0);
        JScrollPane editorScroll = Ui.scroll(editorArea);
        editorScroll.setRowHeaderView(new LineGutter(editorArea));
        editorScroll.getRowHeader().setBackground(Color.WHITE);

        JPanel editorPane = new JPanel(new BorderLayout());
        editorPane.setBackground(Color.WHITE);
        editorPane.add(Ui.sectionHeader("HDL netlist editor", fileLabel), BorderLayout.NORTH);
        editorPane.add(editorScroll, BorderLayout.CENTER);

        // --- Tabbed Pane (Right Side) ---
        JTabbedPane rightTabs = new JTabbedPane();
        rightTabs.setUI(new PlainTabsUI());
        rightTabs.setBackground(Color.WHITE);
        rightTabs.setOpaque(true);
        rightTabs.setFont(Theme.ui(Font.PLAIN, 13f));
        rightTabs.setBorder(null);

        // Tab 1: Console
        logConsole = new CodeArea(13f, Colorizers::telemetry,
                "Run a simulation to see its telemetry report here.", false);
        logConsole.setEditable(false);
        rightTabs.addTab("Telemetry log", Ui.scroll(logConsole));

        // Tab 2: Waveform Viewer
        waveformPanel = new WaveformPanel();
        rightTabs.addTab("Waveform viewer", createWaveformTab());

        schematicTab = createSchematicTab();
        rightTabs.addTab("Schematic", schematicTab);

        rightTabs.addTab("Analysis", createAnalysisPanel());
        rightTabs.addChangeListener(e -> {
            if (rightTabs.getSelectedComponent() == schematicTab) {
                refreshSchematic();
            }
        });

        // --- Split Pane Layout ---
        JSplitPane splitPane = Ui.split(JSplitPane.HORIZONTAL_SPLIT, editorPane, rightTabs);
        splitPane.setDividerLocation(Math.min(460, getWidth() * 2 / 5));
        splitPane.setResizeWeight(0.35);

        // --- Status bar ---
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                new EmptyBorder(5, 12, 5, 12)));
        statusLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
        caretLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
        footer.add(activityLabel, BorderLayout.WEST);
        footer.add(Ui.row(16, statusLabel, caretLabel), BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        // --- Action Listeners ---
        openBtn.addActionListener(e -> openFile());
        saveBtn.addActionListener(e -> saveFile(false));
        saveAsBtn.addActionListener(e -> saveFile(true));
        runBtn.addActionListener(e -> runSimulation());
        stepBtn.addActionListener(e -> stepSimulation());
        resetBtn.addActionListener(e -> resetSimulation());
        busyButtons.addAll(List.of(runBtn, stepBtn, resetBtn));

        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask), openBtn);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask), saveBtn);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask | InputEvent.SHIFT_DOWN_MASK), saveAsBtn);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), runBtn);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0), stepBtn);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F5, InputEvent.SHIFT_DOWN_MASK), resetBtn);

        watchEditor();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                editorArea.requestFocusInWindow();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmDiscardChanges("closing")) {
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    private void bindKey(KeyStroke stroke, JButton button) {
        String key = "dashboard." + stroke;
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, key);
        root.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (button.isEnabled()) {
                    button.doClick(0);
                }
            }
        });
    }

    /** JTextArea.read() replaces the document, so these listeners move to the new one. */
    private void watchEditor() {
        DocumentListener changes = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { editorChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { editorChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        };
        editorArea.getDocument().addDocumentListener(changes);
        editorArea.getDocument().addUndoableEditListener(undo);
        editorArea.addPropertyChangeListener("document", e -> {
            if (e.getOldValue() instanceof Document) {
                ((Document) e.getOldValue()).removeDocumentListener(changes);
                ((Document) e.getOldValue()).removeUndoableEditListener(undo);
            }
            if (e.getNewValue() instanceof Document) {
                ((Document) e.getNewValue()).addDocumentListener(changes);
                ((Document) e.getNewValue()).addUndoableEditListener(undo);
            }
            undo.discardAllEdits();
            errorHighlight = null;
            editorChanged();
            setDirty(false);
        });
        editorArea.addCaretListener(e -> {
            try {
                int offset = e.getDot();
                int line = editorArea.getLineOfOffset(offset);
                caretLabel.setText("Ln " + (line + 1) + ", Col "
                        + (offset - editorArea.getLineStartOffset(line) + 1));
            } catch (BadLocationException ignored) {
                // The caret is moving between documents; the next event corrects the readout.
            }
        });

        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap keys = editorArea.getInputMap();
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), "dashboard.undo");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask), "dashboard.redo");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK), "dashboard.redo");
        editorArea.getActionMap().put("dashboard.undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undo.canUndo()) {
                    undo.undo();
                }
            }
        });
        editorArea.getActionMap().put("dashboard.redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undo.canRedo()) {
                    undo.redo();
                }
            }
        });
    }

    private void editorChanged() {
        setDirty(true);
        clearErrorHighlight();
        // A step-through session belongs to the netlist it started from, so an edit ends it.
        if (debugger != null) {
            debugger = null;
            debugLogger = null;
            updateDebugStatus();
        }
        if (busy) {
            enabledBeforeBusy.put(stepBtn, true);
        } else {
            stepBtn.setEnabled(true);
        }
        schematicTimer.restart();
    }

    private void setDirty(boolean value) {
        dirty = value;
        String name = currentFile == null ? "Untitled netlist" : currentFile.getName();
        if (name.length() > 34) {
            name = name.substring(0, 20) + "..." + name.substring(name.length() - 11);
        }
        fileLabel.setText(dirty ? name + " (unsaved)" : name);
        fileLabel.setToolTipText(currentFile == null ? null : currentFile.getAbsolutePath());
    }

    private boolean confirmDiscardChanges(String action) {
        if (!dirty) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "The netlist has unsaved changes. Discard them before " + action + "?",
                "Unsaved changes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private void setActivity(String text, Color color) {
        activityLabel.setText(text);
        activityLabel.setForeground(color);
    }

    /** Selects a line the parser reported as wrong and marks it until the next edit. */
    private void highlightErrorLine(String message) {
        clearErrorHighlight();
        if (message == null) {
            return;
        }
        Matcher matcher = ERROR_LINE.matcher(message);
        if (!matcher.find()) {
            return;
        }
        try {
            int line = Integer.parseInt(matcher.group(1)) - 1;
            if (line < 0 || line >= editorArea.getLineCount()) {
                return;
            }
            int start = editorArea.getLineStartOffset(line);
            errorHighlight = editorArea.getHighlighter().addHighlight(start, editorArea.getLineEndOffset(line),
                    new DefaultHighlighter.DefaultHighlightPainter(Theme.ERROR_BAND));
            editorArea.setCaretPosition(start);
        } catch (BadLocationException | NumberFormatException ignored) {
            // The message pointed outside this text, so there is nothing to mark.
        }
    }

    private void clearErrorHighlight() {
        if (errorHighlight != null) {
            editorArea.getHighlighter().removeHighlight(errorHighlight);
            errorHighlight = null;
        }
    }

    private JPanel createWaveformTab() {
        ColorButton zoomIn = new ColorButton("Zoom in", Theme.SLATE);
        ColorButton zoomOut = new ColorButton("Zoom out", Theme.SLATE);
        ColorButton fit = new ColorButton("Fit", Theme.SLATE);
        ColorButton clearMarker = new ColorButton("Clear marker", Theme.SLATE);
        ColorButton saveImage = new ColorButton("Save image", Theme.BLUE);
        JTextField filter = new SlimField("");
        filter.setColumns(10);
        filter.setToolTipText("Show only signals whose name contains this text");

        JPanel controls = new JPanel(new BorderLayout(10, 0));
        controls.setBackground(Color.WHITE);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(6, 10, 6, 10)));
        controls.add(Ui.row(6, zoomIn, zoomOut, fit, clearMarker, saveImage), BorderLayout.WEST);
        controls.add(Ui.row(6, Ui.label("Filter", Theme.MUTED, Theme.ui(Font.PLAIN, 12f)), filter),
                BorderLayout.EAST);

        JScrollPane waveScroll = Ui.scroll(waveformPanel);
        waveScroll.getVerticalScrollBar().setUnitIncrement(16);
        waveScroll.getHorizontalScrollBar().setUnitIncrement(16);

        JLabel readout = Ui.label(" ", Theme.MUTED, Theme.ui(Font.PLAIN, 12f));
        readout.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                new EmptyBorder(4, 10, 4, 10)));
        waveformPanel.setReadoutListener(readout::setText);

        zoomIn.addActionListener(e -> waveformPanel.zoomIn());
        zoomOut.addActionListener(e -> waveformPanel.zoomOut());
        fit.addActionListener(e -> waveformPanel.fitToWindow());
        clearMarker.addActionListener(e -> waveformPanel.clearMarker());
        saveImage.addActionListener(e -> saveWaveformImage());
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { waveformPanel.setFilter(filter.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { waveformPanel.setFilter(filter.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });

        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(Color.WHITE);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(waveScroll, BorderLayout.CENTER);
        tab.add(readout, BorderLayout.SOUTH);
        return tab;
    }

    private JPanel createSchematicTab() {
        ColorButton zoomIn = new ColorButton("Zoom in", Theme.SLATE);
        ColorButton zoomOut = new ColorButton("Zoom out", Theme.SLATE);
        ColorButton fit = new ColorButton("Fit", Theme.SLATE);
        ColorButton saveImage = new ColorButton("Save image", Theme.BLUE);
        schematicTimer.setRepeats(false);

        JPanel controls = new JPanel(new BorderLayout());
        controls.setBackground(Color.WHITE);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(6, 10, 6, 10)));
        controls.add(Ui.row(6, zoomIn, zoomOut, fit, saveImage), BorderLayout.WEST);

        JScrollPane scroll = Ui.scroll(schematicPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);

        timeSlider.setUI(new PlainSliderUI(timeSlider));
        timeSlider.setOpaque(false);
        timeSlider.setFocusable(false);
        timeLabel.setPreferredSize(new Dimension(90, 18));
        JPanel scrubber = new JPanel(new BorderLayout(8, 0));
        scrubber.setOpaque(false);
        scrubber.add(previousEdgeBtn, BorderLayout.WEST);
        scrubber.add(timeSlider, BorderLayout.CENTER);
        scrubber.add(Ui.row(8, nextEdgeBtn, timeLabel), BorderLayout.EAST);
        JLabel readout = Ui.label(" ", Theme.MUTED, Theme.ui(Font.PLAIN, 12f));
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setBackground(Color.WHITE);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                new EmptyBorder(6, 10, 4, 10)));
        bottom.add(scrubber, BorderLayout.NORTH);
        bottom.add(readout, BorderLayout.SOUTH);
        schematicPanel.setReadoutListener(readout::setText);

        zoomIn.addActionListener(e -> schematicPanel.zoomIn());
        zoomOut.addActionListener(e -> schematicPanel.zoomOut());
        fit.addActionListener(e -> schematicPanel.fitToWindow());
        saveImage.addActionListener(e -> saveSchematicImage());
        timeSlider.addChangeListener(e -> {
            if (!updatingSlider) {
                showSchematicValues(timeSlider.getValue());
            }
        });
        previousEdgeBtn.addActionListener(e ->
                scrubTo(waveformPanel.previousEdge(timeSlider.getValue())));
        nextEdgeBtn.addActionListener(e ->
                scrubTo(waveformPanel.nextEdge(timeSlider.getValue())));
        setScrubberEnabled(false);

        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(Color.WHITE);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(scroll, BorderLayout.CENTER);
        tab.add(bottom, BorderLayout.SOUTH);
        return tab;
    }

    /** Redraws the schematic from the editor. Parsing resets shared engine state, so it waits for running work. */
    private void refreshSchematic() {
        if (busy) {
            schematicTimer.restart();
            return;
        }
        if (schematicTab == null || !schematicTab.isShowing()) {
            return;
        }
        String netlist = editorArea.getText();
        if (netlist.isBlank()) {
            schematicPanel.showMessage("The netlist is empty. Add wires and gates to draw a schematic.");
        } else {
            try {
                NetlistParser parser = new NetlistParser(new SimulationContext());
                parser.parseString(netlist);
                schematicPanel.setCircuit(SchematicLayout.build(parser));
            } catch (RuntimeException ex) {
                schematicPanel.showMessage("Fix the netlist to draw its schematic. " + ex.getMessage());
            }
        }
        boolean current = netlist.equals(simulatedNetlist) && waveformPanel.hasSignals();
        setScrubberEnabled(current);
        if (current) {
            updatingSlider = true;
            timeSlider.setMaximum((int) Math.min(Integer.MAX_VALUE, waveformPanel.getMaxTime()));
            updatingSlider = false;
            showSchematicValues(timeSlider.getValue());
        } else {
            schematicPanel.setValues(null);
            timeLabel.setText(" ");
        }
    }

    private void setScrubberEnabled(boolean enabled) {
        timeSlider.setEnabled(enabled);
        previousEdgeBtn.setEnabled(enabled);
        nextEdgeBtn.setEnabled(enabled);
        timeSlider.setToolTipText(enabled ? "Choose the time whose wire values are shown"
                : "Run a simulation to see wire values");
    }

    /** Moves the schematic to a time after a run or step, whether or not its tab is open. */
    private void scrubTo(long time) {
        updatingSlider = true;
        timeSlider.setMaximum((int) Math.min(Integer.MAX_VALUE, waveformPanel.getMaxTime()));
        timeSlider.setValue((int) Math.min(Integer.MAX_VALUE, time));
        updatingSlider = false;
        refreshSchematic();
    }

    private void showSchematicValues(long time) {
        timeLabel.setText(time + " ns");
        schematicPanel.setValues(wire -> waveformPanel.valueAt(wire, time));
    }

    private void saveSchematicImage() {
        if (!schematicPanel.hasCircuit()) {
            setActivity("Fix the netlist before saving the schematic", Theme.ERROR);
            return;
        }
        JFileChooser chooser = newChooser();
        chooser.setSelectedFile(new File("schematic.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION
                && confirmOverwrite(chooser.getSelectedFile())) {
            File file = chooser.getSelectedFile();
            try {
                schematicPanel.exportPng(file);
                setActivity("Saved " + file.getName(), Theme.GREEN);
            } catch (IOException ex) {
                setActivity("Could not save " + file.getName() + ": " + ex.getMessage(), Theme.ERROR);
            }
        }
    }

    private JPanel createAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        ColorButton validateButton = new ColorButton("Validate", Theme.TEAL);
        ColorButton timingButton = new ColorButton("Analyze timing", Theme.BLUE);
        ColorButton truthTableButton = new ColorButton("Generate truth table", Theme.BLUE);
        ColorButton detectButton = new ColorButton("Detect wires", Theme.SLATE);
        detectButton.setToolTipText("Fill in the input and output wires from the current netlist");

        JPanel fields = new JPanel(new GridLayout(1, 2, 12, 0));
        fields.setOpaque(false);
        fields.add(Ui.labeled("Input wires (comma separated)", inputsField));
        fields.add(Ui.labeled("Output wires (comma separated)", outputsField));
        JPanel analysisActions = new JPanel(new BorderLayout());
        analysisActions.setOpaque(false);
        analysisActions.setBorder(new EmptyBorder(10, 0, 0, 0));
        analysisActions.add(Ui.row(6, detectButton, truthTableButton, timingButton, validateButton),
                BorderLayout.WEST);

        JPanel controls = new JPanel(new BorderLayout());
        controls.setBackground(Color.WHITE);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                new EmptyBorder(10, 12, 10, 12)));
        controls.add(fields, BorderLayout.CENTER);
        controls.add(analysisActions, BorderLayout.SOUTH);

        JTextArea comparisonArea = new CodeArea(13f, Colorizers::netlist, null, false);
        comparisonArea.setText("# Paste a second netlist here for equivalence checking\n");
        comparisonArea.setCaretPosition(0);
        ColorButton equivalenceButton = new ColorButton("Check equivalence", Theme.BLUE);
        JPanel comparisonPanel = new JPanel(new BorderLayout());
        comparisonPanel.setBackground(Color.WHITE);
        comparisonPanel.add(Ui.sectionHeader("Comparison netlist", equivalenceButton), BorderLayout.NORTH);
        comparisonPanel.add(Ui.scroll(comparisonArea), BorderLayout.CENTER);

        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        configureTruthTableView();
        JPanel tableCard = new JPanel(new BorderLayout());
        tableCard.setBackground(Color.WHITE);
        JScrollPane tableScroll = Ui.scroll(truthTableView);
        tableCard.add(tableScroll, BorderLayout.CENTER);
        truthSummary.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                new EmptyBorder(4, 12, 4, 12)));
        tableCard.add(truthSummary, BorderLayout.SOUTH);
        resultHost.setBackground(Color.WHITE);
        resultHost.add(Ui.scroll(resultArea), "text");
        resultHost.add(tableCard, "table");
        exportCsvBtn.setEnabled(false);
        exportCsvBtn.addActionListener(e -> exportTruthTable());

        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBackground(Color.WHITE);
        resultPanel.add(Ui.sectionHeader("Analysis result", exportCsvBtn), BorderLayout.NORTH);
        resultPanel.add(resultHost, BorderLayout.CENTER);

        detectButton.addActionListener(event -> {
            String netlist = editorArea.getText();
            runTask("Detecting wires...", () -> service.detectPorts(netlist), ports -> {
                applyDetectedWires(ports);
                showText(ports.getInputs().isEmpty() && ports.getOutputs().isEmpty()
                        ? "No input or output wires were found in this netlist."
                        : "Inputs: " + String.join(", ", ports.getInputs())
                        + "\nOutputs: " + String.join(", ", ports.getOutputs()), Theme.TEXT);
                setActivity("Wires detected", Theme.GREEN);
            }, "Wire detection failed");
        });
        validateButton.addActionListener(event -> {
            String netlist = editorArea.getText();
            runTask("Validating...", () -> service.validate(netlist), result -> {
                showText(result.getMessage(), result.isValid() ? Theme.GREEN : Theme.ERROR);
                setActivity(result.getMessage(), result.isValid() ? Theme.GREEN : Theme.ERROR);
            }, "Validation failed");
        });
        timingButton.addActionListener(event -> {
            String netlist = editorArea.getText();
            runTask("Analyzing timing...", () -> service.analyze(netlist), result -> {
                String frequency = result.getCriticalPathDelay() == 0
                        ? "not applicable (no gate delay on any path)"
                        : String.format("%.2f MHz", result.getMaxClockFrequencyMHz());
                showText("Critical path delay: " + result.getCriticalPathDelay() + " ns\n"
                        + "Maximum clock frequency: " + frequency, Theme.TEXT);
                setActivity("Timing analysis finished", Theme.GREEN);
            }, "Analysis failed");
        });
        truthTableButton.addActionListener(event -> {
            String netlist = editorArea.getText();
            List<String> inputs = names(inputsField.getText());
            List<String> outputs = names(outputsField.getText());
            runTask("Generating truth table...", () -> {
                WireChoice wires = chooseWires(netlist, inputs, outputs);
                return new TruthResult(wires,
                        service.truthTable(netlist, wires.inputs, wires.outputs));
            }, result -> {
                setWireFields(result.wires);
                showTruthTable(result.table, result.wires.note);
                setActivity("Truth table generated", Theme.GREEN);
            }, "Truth-table generation failed");
        });
        equivalenceButton.addActionListener(event -> {
            String netlist = editorArea.getText();
            String comparison = comparisonArea.getText();
            List<String> inputs = names(inputsField.getText());
            List<String> outputs = names(outputsField.getText());
            runTask("Checking equivalence...", () -> {
                WireChoice wires = chooseWires(netlist, inputs, outputs);
                return new EquivalenceRun(wires,
                        service.equivalent(netlist, comparison, wires.inputs, wires.outputs));
            }, run -> {
                setWireFields(run.wires);
                CircuitEquivalenceChecker.EquivalenceResult result = run.result;
                if (result.isEquivalent()) {
                    showText("Equivalent for all input combinations.", Theme.GREEN);
                    setActivity("Circuits are equivalent", Theme.GREEN);
                } else {
                    showText("Not equivalent.\nCounterexample row: "
                            + result.getCounterexampleRow() + "\nInputs: "
                            + labelled(run.wires.inputs, result.getFirstRow().getInputStates())
                            + "\nCurrent outputs: "
                            + labelled(run.wires.outputs, result.getFirstRow().getOutputStates())
                            + "\nComparison outputs: "
                            + labelled(run.wires.outputs, result.getSecondRow().getOutputStates()), Theme.ERROR);
                    setActivity("Circuits differ", Theme.ERROR);
                }
            }, "Equivalence check failed");
        });
        busyButtons.addAll(List.of(detectButton, validateButton, timingButton, truthTableButton,
                equivalenceButton, exportCsvBtn));

        JSplitPane body = Ui.split(JSplitPane.VERTICAL_SPLIT, comparisonPanel, resultPanel);
        body.setResizeWeight(0.35);
        body.setDividerLocation(190);

        panel.add(controls, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** Runs analysis work off the event thread, with the action buttons off until it finishes. */
    private <T> void runTask(String activity, Callable<T> work, Consumer<T> onSuccess, String failurePrefix) {
        setBusy(true);
        setActivity(activity, Theme.MUTED);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    onSuccess.accept(get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    showText(failurePrefix + ": " + cause.getMessage(), Theme.ERROR);
                    setActivity(failurePrefix, Theme.ERROR);
                    highlightErrorLine(cause.getMessage());
                }
            }
        }.execute();
    }

    private void setBusy(boolean value) {
        if (value == busy) {
            return;
        }
        busy = value;
        for (JButton button : busyButtons) {
            if (value) {
                enabledBeforeBusy.put(button, button.isEnabled());
                button.setEnabled(false);
            } else {
                button.setEnabled(enabledBeforeBusy.getOrDefault(button, true));
            }
        }
    }

    /**
     * Uses the wires typed in the fields when they exist in this netlist. When none of them do,
     * the fields still describe a previous netlist, so the wires are detected instead.
     */
    private WireChoice chooseWires(String netlist, List<String> inputs, List<String> outputs) {
        CircuitPorts ports = service.detectPorts(netlist);
        Set<String> typed = new LinkedHashSet<>(inputs);
        typed.addAll(outputs);
        List<String> missing = new ArrayList<>();
        for (String name : typed) {
            if (!ports.getWires().contains(name)) {
                missing.add(name);
            }
        }
        if (typed.isEmpty() || inputs.isEmpty() || outputs.isEmpty() || missing.size() == typed.size()) {
            if (ports.getInputs().isEmpty() || ports.getOutputs().isEmpty()) {
                throw new IllegalArgumentException("Could not detect input and output wires. "
                        + "Enter them above. Wires in this netlist: " + listWires(ports.getWires()));
            }
            String note = typed.isEmpty() ? "Wires were detected from the netlist."
                    : "The wires above were not in this netlist, so they were detected again.";
            return new WireChoice(ports.getInputs(), ports.getOutputs(), note);
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Not in this netlist: " + String.join(", ", missing)
                    + ". Wires in this netlist: " + listWires(ports.getWires()));
        }
        List<String> undriven = new ArrayList<>(ports.getInputs());
        undriven.removeAll(inputs);
        String note = undriven.isEmpty() ? null
                : "Not listed as inputs, so left undriven: " + String.join(", ", undriven)
                + ". Outputs that depend on them can show X.";
        return new WireChoice(inputs, outputs, note);
    }

    private static String listWires(List<String> wires) {
        if (wires.size() <= MAX_WIRES_IN_MESSAGE) {
            return String.join(", ", wires);
        }
        return String.join(", ", wires.subList(0, MAX_WIRES_IN_MESSAGE)) + " and "
                + (wires.size() - MAX_WIRES_IN_MESSAGE) + " more";
    }

    private void setWireFields(WireChoice wires) {
        inputsField.setText(String.join(",", wires.inputs));
        outputsField.setText(String.join(",", wires.outputs));
    }

    private void applyDetectedWires(CircuitPorts ports) {
        if (!ports.getInputs().isEmpty() || !ports.getOutputs().isEmpty()) {
            inputsField.setText(String.join(",", ports.getInputs()));
            outputsField.setText(String.join(",", ports.getOutputs()));
        }
    }

    private static String labelled(List<String> names, List<String> states) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < names.size() && i < states.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(names.get(i)).append('=').append(states.get(i));
        }
        return text.toString();
    }

    private void showText(String text, Color color) {
        resultArea.setForeground(color);
        resultArea.setText(text);
        resultArea.setCaretPosition(0);
        lastTruthTable = null;
        enabledBeforeBusy.put(exportCsvBtn, false);
        exportCsvBtn.setEnabled(false);
        resultCards.show(resultHost, "text");
    }

    private void configureTruthTableView() {
        truthTableView.setBackground(Color.WHITE);
        truthTableView.setForeground(Theme.TEXT);
        truthTableView.setFont(Theme.mono(13f));
        truthTableView.setRowHeight(24);
        truthTableView.setGridColor(Theme.LINE);
        truthTableView.setShowVerticalLines(true);
        truthTableView.setShowHorizontalLines(true);
        truthTableView.setSelectionBackground(Theme.SELECTION);
        truthTableView.setSelectionForeground(Theme.TEXT);
        truthTableView.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        truthTableView.setFillsViewportHeight(true);
        truthTableView.getTableHeader().setReorderingAllowed(false);
        truthTableView.getTableHeader().setBackground(Theme.HEADER);
        truthTableView.getTableHeader().setForeground(Theme.TEXT);
        truthTableView.getTableHeader().setFont(Theme.ui(Font.BOLD, 12f));
    }

    private void showTruthTable(TruthTable table, String note) {
        lastTruthTable = table;
        Object[] columns = new Object[table.getInputs().size() + table.getOutputs().size() + 1];
        columns[0] = "Row";
        int column = 1;
        for (String name : table.getInputs()) {
            columns[column++] = name;
        }
        for (String name : table.getOutputs()) {
            columns[column++] = name;
        }
        Object[][] rows = new Object[table.getRows().size()][columns.length];
        for (int r = 0; r < rows.length; r++) {
            TruthTable.Row row = table.getRows().get(r);
            rows[r][0] = r;
            int c = 1;
            for (String state : row.getInputStates()) {
                rows[r][c++] = state;
            }
            for (String state : row.getOutputStates()) {
                rows[r][c++] = state;
            }
        }
        truthModel.setDataVector(rows, columns);

        int firstOutput = table.getInputs().size() + 1;
        FontMetrics header = truthTableView.getFontMetrics(truthTableView.getTableHeader().getFont());
        for (int i = 0; i < columns.length; i++) {
            TableColumn tableColumn = truthTableView.getColumnModel().getColumn(i);
            tableColumn.setCellRenderer(new StateCellRenderer(i == 0, i >= firstOutput));
            tableColumn.setPreferredWidth(Math.max(52, header.stringWidth(String.valueOf(columns[i])) + 24));
        }

        String summary = table.getRows().size() + " rows. Shaded columns are outputs.";
        truthSummary.setText(note == null ? summary : summary + " " + note);
        truthSummary.setToolTipText(note);
        enabledBeforeBusy.put(exportCsvBtn, true);
        exportCsvBtn.setEnabled(true);
        resultCards.show(resultHost, "table");
    }

    private void exportTruthTable() {
        if (lastTruthTable == null) {
            return;
        }
        JFileChooser chooser = newChooser();
        chooser.setSelectedFile(new File("truth-table.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION
                && confirmOverwrite(chooser.getSelectedFile())) {
            File file = chooser.getSelectedFile();
            try {
                Files.write(file.toPath(), lastTruthTable.toCsv().getBytes(StandardCharsets.UTF_8));
                setActivity("Saved " + file.getName(), Theme.GREEN);
            } catch (IOException ex) {
                setActivity("Could not save " + file.getName() + ": " + ex.getMessage(), Theme.ERROR);
            }
        }
    }

    private void saveWaveformImage() {
        if (!waveformPanel.hasSignals()) {
            setActivity("Run a simulation before saving the waveform", Theme.ERROR);
            return;
        }
        JFileChooser chooser = newChooser();
        chooser.setSelectedFile(new File("waveform.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION
                && confirmOverwrite(chooser.getSelectedFile())) {
            File file = chooser.getSelectedFile();
            try {
                waveformPanel.exportPng(file);
                setActivity("Saved " + file.getName(), Theme.GREEN);
            } catch (IOException ex) {
                setActivity("Could not save " + file.getName() + ": " + ex.getMessage(), Theme.ERROR);
            }
        }
    }

    private boolean confirmOverwrite(File file) {
        return !file.exists() || JOptionPane.showConfirmDialog(this,
                file.getName() + " already exists. Replace it?", "Replace file",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static List<String> names(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    /** Builds the chooser under the platform look and feel, so the dialog looks native. */
    private static JFileChooser newChooser() {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            return new JFileChooser(".");
        } catch (Exception | Error ex) {
            return new JFileChooser(".");
        } finally {
            try {
                UIManager.setLookAndFeel(previous);
            } catch (UnsupportedLookAndFeelException ignored) {
                // The previous look and feel was already in use, so it stays supported.
            }
        }
    }

    private static JFileChooser newNetlistChooser() {
        JFileChooser chooser = newChooser();
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Netlist files (*.netlist)", "netlist"));
        return chooser;
    }

    private void openFile() {
        if (!confirmDiscardChanges("opening another file")) {
            return;
        }
        JFileChooser chooser = newNetlistChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File chosen = chooser.getSelectedFile();
            try (BufferedReader r = new BufferedReader(new FileReader(chosen))) {
                currentFile = chosen;
                editorArea.read(r, null);
                setDirty(false);
                log("Loaded file: " + currentFile.getAbsolutePath());
                setActivity("Loaded " + currentFile.getName(), Theme.MUTED);
                detectWiresQuietly();
            } catch (IOException ex) {
                log("Error reading file: " + ex.getMessage());
                setActivity("Could not read " + chosen.getName(), Theme.ERROR);
            }
        }
    }

    /** Matches the analysis fields to a newly opened netlist. A netlist with errors keeps the old values. */
    private void detectWiresQuietly() {
        try {
            applyDetectedWires(service.detectPorts(editorArea.getText()));
        } catch (RuntimeException ignored) {
            // Running or analyzing this netlist reports the same error with its line number.
        }
    }

    private void saveFile(boolean chooseNewFile) {
        if (currentFile == null || chooseNewFile) {
            JFileChooser chooser = newNetlistChooser();
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION
                    && confirmOverwrite(chooser.getSelectedFile())) {
                currentFile = chooser.getSelectedFile();
            } else {
                return;
            }
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(currentFile))) {
            editorArea.write(w);
            setDirty(false);
            log("Saved: " + currentFile.getAbsolutePath());
            setActivity("Saved " + currentFile.getName(), Theme.MUTED);
        } catch (IOException ex) {
            log("Error saving file: " + ex.getMessage());
            setActivity("Could not save " + currentFile.getName(), Theme.ERROR);
        }
    }

    private void runSimulation() {
        logConsole.setText("");
        log("===============================================");
        log("[+] INITIALIZING SIMULATION");
        log("===============================================");
        setBusy(true);
        setActivity("Simulating...", Theme.MUTED);
        clearErrorHighlight();
        final String netlist = editorArea.getText();
        final String waveformPath = new File("output.vcd").getAbsolutePath();
        new SwingWorker<SimulationRunner.SimulationResult, Void>() {
            @Override
            protected SimulationRunner.SimulationResult doInBackground() {
                return service.simulate(netlist, waveformPath);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    SimulationRunner.SimulationResult result = get();
                    waveformPanel.loadVcd(waveformPath);
                    simulatedNetlist = netlist;
                    scrubTo(waveformPanel.getMaxTime());
                    log("\n===============================================");
                    log("[+] SIMULATION TELEMETRY REPORT");
                    log("===============================================");
                    log(String.format("%-25s %d nodes", "Circuit Topology:",
                            result.getWireCount() + result.getComponentCount()));
                    log(String.format("%-25s %d ns", "Simulated Time:",
                            result.getSimulatedTime()));
                    log(String.format("%-25s %d ns", "Critical Path Delay:",
                            result.getCriticalPathDelay()));
                    log(String.format("%-25s %d events", "Total Events Processed:",
                            result.getEventsProcessed()));
                    log(String.format("%-25s %d", "Peak Queue Depth:",
                            result.getPeakQueueDepth()));
                    log(String.format("%-25s %d ms", "CPU Compute Time:",
                            result.getComputeTimeMillis()));
                    log(String.format("%-25s %s", "Waveform Exported:", waveformPath));
                    log("===============================================");
                    setActivity("Simulation finished", Theme.GREEN);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    log("\n[!] SIMULATION FAILED: " + cause.getMessage());
                    setActivity("Simulation failed. See the telemetry log.", Theme.ERROR);
                    highlightErrorLine(cause.getMessage());
                }
            }
        }.execute();
    }

    private void prepareDebugSession() {
        debugContext = new SimulationContext();
        debugParser = new NetlistParser(debugContext);
        debugParser.parseString(editorArea.getText());
        if (!new TopologyValidator().validateNoCombinationalLoops(debugParser.getComponents())) {
            throw new IllegalStateException("Unclocked combinational loop detected");
        }
        debugWaveformPath = new File("debug-output.vcd").getAbsolutePath();
        debugLogger = new VcdLogger(debugWaveformPath, debugContext);
        debugParser.getAllWires().forEach(debugLogger::monitorWire);
        for (DigitalComponent component : debugParser.getComponents()) {
            if (component instanceof Clock) {
                ((Clock) component).generateClockPulses();
            }
        }
        debugger = new SimulationDebugger(debugContext);
        stepBtn.setEnabled(true);
        resetBtn.setEnabled(true);
        updateDebugStatus();
    }

    private void stepSimulation() {
        try {
            if (debugger == null) {
                prepareDebugSession();
                log("Step session started.");
            }
            if (!debugger.step()) {
                log("Simulation complete.");
                stepBtn.setEnabled(false);
                setActivity("Simulation complete. Press Reset to step again.", Theme.GREEN);
            } else {
                setActivity("Stepped one event", Theme.MUTED);
            }
            exportDebugWaveform();
            updateDebugStatus();
        } catch (Exception ex) {
            log("[!] STEP FAILED: " + ex.getMessage());
            setActivity("Step failed. See the telemetry log.", Theme.ERROR);
            highlightErrorLine(ex.getMessage());
        }
    }

    private void resetSimulation() {
        try {
            prepareDebugSession();
            exportDebugWaveform();
            log("Simulation reset.");
            setActivity("Simulation reset", Theme.MUTED);
        } catch (Exception ex) {
            log("[!] RESET FAILED: " + ex.getMessage());
            setActivity("Reset failed. See the telemetry log.", Theme.ERROR);
            highlightErrorLine(ex.getMessage());
        }
    }

    private void exportDebugWaveform() throws IOException {
        if (debugLogger != null) {
            debugLogger.exportFile();
            waveformPanel.loadVcd(debugWaveformPath);
            simulatedNetlist = editorArea.getText();
            scrubTo(debugger.getCurrentTime());
        }
    }

    private void updateDebugStatus() {
        if (debugger == null) {
            statusLabel.setText("No simulation loaded");
            return;
        }
        statusLabel.setText(String.format("Time: %dns | Pending: %d",
                debugger.getCurrentTime(), debugger.getPendingEventCount()));
    }

    private void log(String message) {
        logConsole.append(message + "\n");
    }

    private String getDefaultNetlistSample() {
        return "# Sample Full Adder Netlist\n" +
               "SIM_TIME 100\n\n" +
               "WIRE Input_A\n" +
               "WIRE Input_B\n" +
               "WIRE Input_Cin\n" +
               "WIRE Sum\n" +
               "WIRE CarryOut\n" +
               "WIRE Wire1\n" +
               "WIRE Wire2\n" +
               "WIRE Wire3\n\n" +
               "XOR g1 10 Input_A Input_B Wire1\n" +
               "XOR g2 10 Wire1 Input_Cin Sum\n" +
               "AND g3 10 Input_A Input_B Wire2\n" +
               "AND g4 10 Wire1 Input_Cin Wire3\n" +
               "OR  g5 10 Wire2 Wire3 CarryOut\n\n" +
               "STIMULUS 0 Input_A 1\n" +
               "STIMULUS 0 Input_B 0\n" +
               "STIMULUS 0 Input_Cin 1\n" +
               "STIMULUS 50 Input_B 1\n";
    }

    public static void main(String[] args) {
        if (System.getProperty("awt.useSystemAAFontSettings") == null) {
            System.setProperty("awt.useSystemAAFontSettings", "lcd");
        }
        SwingUtilities.invokeLater(() -> {
            new SimulatorDashboard().setVisible(true);
        });
    }

    private static final class WireChoice {
        final List<String> inputs;
        final List<String> outputs;
        final String note;

        WireChoice(List<String> inputs, List<String> outputs, String note) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.note = note;
        }
    }

    private static final class TruthResult {
        final WireChoice wires;
        final TruthTable table;

        TruthResult(WireChoice wires, TruthTable table) {
            this.wires = wires;
            this.table = table;
        }
    }

    private static final class EquivalenceRun {
        final WireChoice wires;
        final CircuitEquivalenceChecker.EquivalenceResult result;

        EquivalenceRun(WireChoice wires, CircuitEquivalenceChecker.EquivalenceResult result) {
            this.wires = wires;
            this.result = result;
        }
    }

    // ---------------------------------------------------------------------
    // Colours and fonts
    // ---------------------------------------------------------------------

    private static final class Theme {
        static final Color TEXT = new Color(0x1F2328);
        static final Color MUTED = new Color(0x5F6770);
        static final Color FAINT = new Color(0x8A9199);
        static final Color LINE = new Color(0xD8DBDF);
        static final Color HEADER = new Color(0xF3F4F6);
        static final Color OUTPUT_COLUMN = new Color(0xEAF2FC);
        static final Color CURRENT_LINE = new Color(0xF5F6F8);
        static final Color SELECTION = new Color(0xCFE2FA);
        static final Color ERROR_BAND = new Color(0xFBDAD7);
        static final Color DISABLED_FILL = new Color(0xE4E6E9);
        static final Color DISABLED_TEXT = new Color(0x7D848C);

        static final Color BLUE = new Color(0x1D5FBF);
        static final Color GREEN = new Color(0x1B7F3B);
        static final Color TEAL = new Color(0x0B7285);
        static final Color ORANGE = new Color(0xB8500B);
        static final Color SLATE = new Color(0x4B5563);
        static final Color ERROR = new Color(0xB3261E);

        private static final String UI_FAMILY = pick(Font.SANS_SERIF,
                "Segoe UI", "Helvetica Neue", "Noto Sans", "DejaVu Sans");
        private static final String MONO_FAMILY = pick(Font.MONOSPACED,
                "Consolas", "Menlo", "DejaVu Sans Mono", "Liberation Mono");

        private Theme() { }

        static Font ui(int style, float size) {
            return new Font(UI_FAMILY, style, 12).deriveFont(size);
        }

        static Font mono(float size) {
            return new Font(MONO_FAMILY, Font.PLAIN, 12).deriveFont(size);
        }

        private static String pick(String fallback, String... families) {
            Set<String> installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames(Locale.ROOT));
            for (String family : families) {
                if (installed.contains(family)) {
                    return family;
                }
            }
            return fallback;
        }

        static void installDefaults() {
            UIManager.put("ToolTip.background", Color.WHITE);
            UIManager.put("ToolTip.foreground", TEXT);
            UIManager.put("ToolTip.font", ui(Font.PLAIN, 12f));
            UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(LINE), new EmptyBorder(3, 6, 3, 6)));
            UIManager.put("ScrollBar.width", 12);
        }
    }

    // ---------------------------------------------------------------------
    // Layout helpers
    // ---------------------------------------------------------------------

    private static final class Ui {
        private Ui() { }

        static JLabel label(String text, Color color, Font font) {
            JLabel label = new JLabel(text);
            label.setForeground(color);
            label.setFont(font);
            return label;
        }

        /** A vertically centred row with gaps only between its items. */
        static JPanel row(int gap, Component... items) {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            for (int i = 0; i < items.length; i++) {
                c.gridx = i;
                c.insets = new Insets(0, i == 0 ? 0 : gap, 0, 0);
                panel.add(items[i], c);
            }
            return panel;
        }

        static Component gap(int width) {
            return Box.createRigidArea(new Dimension(width, 1));
        }

        static JPanel labeled(String text, JComponent field) {
            JPanel panel = new JPanel(new BorderLayout(0, 4));
            panel.setOpaque(false);
            panel.add(label(text, Theme.MUTED, Theme.ui(Font.PLAIN, 12f)), BorderLayout.NORTH);
            panel.add(field, BorderLayout.CENTER);
            return panel;
        }

        static JPanel sectionHeader(String title, JComponent trailing) {
            JPanel header = new JPanel(new BorderLayout(12, 0));
            header.setBackground(Theme.HEADER);
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                    new EmptyBorder(0, 12, 0, 8)));
            header.setPreferredSize(new Dimension(10, 37));
            header.add(label(title, Theme.TEXT, Theme.ui(Font.BOLD, 12f)), BorderLayout.WEST);
            if (trailing != null) {
                JPanel wrap = new JPanel(new GridBagLayout());
                wrap.setOpaque(false);
                wrap.add(trailing);
                header.add(wrap, BorderLayout.EAST);
            }
            return header;
        }

        static JScrollPane scroll(Component view) {
            JScrollPane scroll = new JScrollPane(view);
            scroll.setBorder(null);
            scroll.setViewportBorder(null);
            scroll.setBackground(Color.WHITE);
            scroll.getViewport().setBackground(Color.WHITE);
            scroll.getVerticalScrollBar().setUI(new PlainScrollBarUI());
            scroll.getHorizontalScrollBar().setUI(new PlainScrollBarUI());
            JPanel corner = new JPanel();
            corner.setBackground(Color.WHITE);
            scroll.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, corner);
            JPanel headerCorner = new JPanel();
            headerCorner.setBackground(Theme.HEADER);
            scroll.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, headerCorner);
            return scroll;
        }

        static JSplitPane split(int orientation, Component first, Component second) {
            JSplitPane split = new JSplitPane(orientation, first, second);
            split.setUI(new PlainSplitUI());
            split.setBorder(null);
            split.setDividerSize(5);
            split.setContinuousLayout(true);
            split.setBackground(Color.WHITE);
            return split;
        }
    }

    // ---------------------------------------------------------------------
    // Components
    // ---------------------------------------------------------------------

    /** A flat, solid-colour button. */
    private static final class ColorButton extends JButton {
        private final Color fill;

        ColorButton(String text, Color fill) {
            super(text);
            this.fill = fill;
            setFont(Theme.ui(Font.PLAIN, 13f));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(getFontMetrics(getFont()).stringWidth(getText()) + 24, 28);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ButtonModel m = getModel();
            Color background = !isEnabled() ? Theme.DISABLED_FILL
                    : m.isPressed() && m.isArmed() ? fill.darker().darker()
                    : m.isRollover() ? fill.darker() : fill;
            g.setColor(background);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
            if (isFocusOwner() && isEnabled()) {
                g.setColor(Color.WHITE);
                g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 3, 3);
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(isEnabled() ? Color.WHITE : Theme.DISABLED_TEXT);
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    private static final class SlimField extends JTextField {
        SlimField(String text) {
            super(text);
            setFont(Theme.mono(13f));
            setForeground(Theme.TEXT);
            setBackground(Color.WHITE);
            setCaretColor(Theme.TEXT);
            setSelectionColor(Theme.SELECTION);
            setSelectedTextColor(Theme.TEXT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xB9BEC5)), new EmptyBorder(4, 6, 4, 6)));
        }
    }

    private static final class StateCellRenderer extends DefaultTableCellRenderer {
        private final boolean rowColumn;
        private final boolean outputColumn;

        StateCellRenderer(boolean rowColumn, boolean outputColumn) {
            this.rowColumn = rowColumn;
            this.outputColumn = outputColumn;
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            if (!isSelected) {
                setBackground(outputColumn ? Theme.OUTPUT_COLUMN : Color.WHITE);
            }
            String text = String.valueOf(value);
            setForeground(rowColumn ? Theme.FAINT
                    : "1".equals(text) ? Theme.GREEN
                    : "0".equals(text) ? Theme.TEXT : Theme.ORANGE);
            return this;
        }
    }

    /** A JTextArea with per-token colour, a current-line band and an empty-state hint. */
    private static final class CodeArea extends JTextArea {
        private final String placeholder;
        private final boolean bandCurrentLine;

        CodeArea(float size, Function<String, Color[]> colorizer, String placeholder, boolean bandCurrentLine) {
            this.placeholder = placeholder;
            this.bandCurrentLine = bandCurrentLine;
            setUI(new ColorizedTextUI(colorizer));
            setOpaque(false);
            setFont(Theme.mono(size));
            setBackground(Color.WHITE);
            setForeground(Theme.TEXT);
            setCaretColor(Theme.TEXT);
            setSelectionColor(Theme.SELECTION);
            setSelectedTextColor(Theme.TEXT);
            setMargin(new Insets(8, 10, 8, 10));
            if (bandCurrentLine) {
                addCaretListener(e -> repaint());
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            graphics.setColor(getBackground());
            graphics.fillRect(0, 0, getWidth(), getHeight());
            if (bandCurrentLine) {
                try {
                    java.awt.geom.Rectangle2D caret = modelToView2D(getCaretPosition());
                    if (caret != null) {
                        graphics.setColor(Theme.CURRENT_LINE);
                        graphics.fillRect(0, (int) caret.getY(), getWidth(), (int) caret.getHeight());
                    }
                } catch (BadLocationException ignored) {
                    // No band for this frame; the caret is moving between documents.
                }
            }
            if (graphics instanceof Graphics2D) {
                Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
                if (hints instanceof RenderingHints) {
                    ((Graphics2D) graphics).addRenderingHints((RenderingHints) hints);
                } else {
                    ((Graphics2D) graphics).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                }
            }
            if (placeholder != null && getDocument().getLength() == 0) {
                graphics.setFont(Theme.ui(Font.PLAIN, 13f));
                graphics.setColor(Theme.MUTED);
                Insets in = getInsets();
                graphics.drawString(placeholder, in.left, in.top + graphics.getFontMetrics().getAscent());
            }
            super.paintComponent(graphics);
        }
    }

    /** Colours are computed at paint time, so the document stays plain and read()/write() behave as usual. */
    private static final class ColorizedTextUI extends BasicTextAreaUI {
        private final Function<String, Color[]> colorizer;

        ColorizedTextUI(Function<String, Color[]> colorizer) {
            this.colorizer = colorizer;
        }

        @Override
        public View create(Element element) {
            JTextComponent component = getComponent();
            boolean wrapped = component instanceof JTextArea && ((JTextArea) component).getLineWrap();
            boolean complexText = Boolean.TRUE.equals(element.getDocument().getProperty("i18n"));
            if (colorizer == null || wrapped || complexText) {
                return super.create(element);
            }
            return new ColorizedView(element, colorizer);
        }
    }

    private static final class ColorizedView extends PlainView {
        private final Function<String, Color[]> colorizer;

        ColorizedView(Element element, Function<String, Color[]> colorizer) {
            super(element);
            this.colorizer = colorizer;
        }

        @Override
        protected float drawUnselectedText(Graphics2D g, float x, float y, int p0, int p1)
                throws BadLocationException {
            Document doc = getDocument();
            Element root = doc.getDefaultRootElement();
            Element line = root.getElement(root.getElementIndex(p0));
            int start = line.getStartOffset();
            Color[] colors = colorizer.apply(doc.getText(start, Math.min(line.getEndOffset(), doc.getLength()) - start));
            Segment segment = new Segment();
            int i = p0;
            while (i < p1) {
                Color color = colorAt(colors, i - start);
                int j = i + 1;
                while (j < p1 && colorAt(colors, j - start) == color) {
                    j++;
                }
                doc.getText(i, j - i, segment);
                g.setColor(color);
                x = Utilities.drawTabbedText(segment, x, y, g, this, i);
                i = j;
            }
            return x;
        }

        private static Color colorAt(Color[] colors, int index) {
            return index < colors.length ? colors[index] : Theme.TEXT;
        }
    }

    private static final class Colorizers {
        private static final Set<String> DIRECTIVES = Set.of("WIRE", "SIM_TIME", "STIMULUS", "ASSERT", "FAULT",
                "DEF", "ENDDEF");

        private Colorizers() { }

        /** Directives in blue, other leading keywords (gate types) in teal, numbers in orange, comments in grey. */
        static Color[] netlist(String line) {
            Color[] colors = new Color[line.length()];
            Arrays.fill(colors, Theme.TEXT);
            int comment = line.indexOf('#');
            int end = comment >= 0 ? comment : line.length();
            if (comment >= 0) {
                Arrays.fill(colors, comment, line.length(), Theme.FAINT);
            }
            boolean first = true;
            int i = 0;
            while (i < end) {
                if (Character.isWhitespace(line.charAt(i))) {
                    i++;
                    continue;
                }
                int j = i;
                while (j < end && !Character.isWhitespace(line.charAt(j))) {
                    j++;
                }
                String token = line.substring(i, j);
                if (first && token.equals(token.toUpperCase(Locale.ROOT))) {
                    Arrays.fill(colors, i, j, DIRECTIVES.contains(token) ? Theme.BLUE : Theme.TEAL);
                } else if (token.chars().allMatch(Character::isDigit)) {
                    Arrays.fill(colors, i, j, Theme.ORANGE);
                }
                first = false;
                i = j;
            }
            return colors;
        }

        /** Failures in red, section markers in blue, rules faint, report labels muted. */
        static Color[] telemetry(String line) {
            Color[] colors = new Color[line.length()];
            Color base = Theme.TEXT;
            if (line.startsWith("[!]") || line.startsWith("Error")) {
                base = Theme.ERROR;
            } else if (line.startsWith("[+]")) {
                base = Theme.BLUE;
            } else if (line.startsWith("====")) {
                base = Theme.FAINT;
            }
            Arrays.fill(colors, base);
            int colon = line.indexOf(':');
            if (base == Theme.TEXT && colon > 0 && colon < 25) {
                Arrays.fill(colors, 0, colon + 1, Theme.MUTED);
            }
            return colors;
        }
    }

    private static final class LineGutter extends JComponent {
        private final JTextArea area;
        private final DocumentListener changes = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        };

        LineGutter(JTextArea area) {
            this.area = area;
            area.getDocument().addDocumentListener(changes);
            area.addCaretListener(e -> repaint());
            area.addPropertyChangeListener("document", e -> {
                if (e.getOldValue() instanceof Document) {
                    ((Document) e.getOldValue()).removeDocumentListener(changes);
                }
                if (e.getNewValue() instanceof Document) {
                    ((Document) e.getNewValue()).addDocumentListener(changes);
                }
                refresh();
            });
        }

        private void refresh() {
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            int digits = Math.max(3, String.valueOf(area.getLineCount()).length());
            int width = getFontMetrics(area.getFont()).charWidth('0') * digits + 16;
            return new Dimension(width, area.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle clip = g.getClipBounds();
            g.setColor(Color.WHITE);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            g.setColor(Theme.LINE);
            g.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);
            g.setFont(area.getFont());
            FontMetrics fm = g.getFontMetrics();
            int lineHeight = area.getFontMetrics(area.getFont()).getHeight();
            int top = area.getInsets().top;
            int current = 0;
            try {
                current = area.getLineOfOffset(area.getCaretPosition());
            } catch (BadLocationException ignored) {
                // Falls back to the first line for this frame.
            }
            int first = Math.max(0, (clip.y - top) / lineHeight);
            int last = Math.min(area.getLineCount() - 1, (clip.y + clip.height - top) / lineHeight);
            int rightEdge = getWidth() - 8;
            for (int line = first; line <= last; line++) {
                String number = String.valueOf(line + 1);
                g.setColor(line == current ? Theme.TEXT : Theme.FAINT);
                g.drawString(number, rightEdge - fm.stringWidth(number), top + line * lineHeight + fm.getAscent());
            }
            g.dispose();
        }
    }

    // ---------------------------------------------------------------------
    // Look-and-feel delegates
    // ---------------------------------------------------------------------

    private static final class PlainScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(0xC4C8CE);
            trackColor = Color.WHITE;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton button = new JButton();
            Dimension none = new Dimension(0, 0);
            button.setPreferredSize(none);
            button.setMinimumSize(none);
            button.setMaximumSize(none);
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
            g.setColor(Color.WHITE);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            g.setColor(isDragging || isThumbRollover() ? new Color(0x9AA0A8) : thumbColor);
            boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            g.fillRect(bounds.x + (vertical ? 3 : 0), bounds.y + (vertical ? 0 : 3),
                    bounds.width - (vertical ? 6 : 0), bounds.height - (vertical ? 0 : 6));
        }

        @Override
        protected void setThumbRollover(boolean active) {
            super.setThumbRollover(active);
            scrollbar.repaint();
        }
    }

    /** A flat track and thumb, in place of the default shaded slider. */
    private static final class PlainSliderUI extends javax.swing.plaf.basic.BasicSliderUI {
        PlainSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(10, 18);
        }

        @Override
        public void paintTrack(Graphics g) {
            int y = trackRect.y + trackRect.height / 2 - 2;
            g.setColor(Theme.LINE);
            g.fillRect(trackRect.x, y, trackRect.width, 4);
            if (slider.isEnabled()) {
                g.setColor(Theme.BLUE);
                g.fillRect(trackRect.x, y, thumbRect.x - trackRect.x, 4);
            }
        }

        @Override
        public void paintThumb(Graphics g) {
            g.setColor(slider.isEnabled() ? Theme.BLUE : Theme.DISABLED_FILL);
            g.fillRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
        }

        @Override
        public void paintFocus(Graphics g) {
            // The slider takes no keyboard focus in this window.
        }
    }

    private static final class PlainSplitUI extends BasicSplitPaneUI {
        @Override
        public BasicSplitPaneDivider createDefaultDivider() {
            return new BasicSplitPaneDivider(this) {
                @Override
                public void paint(Graphics g) {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(Theme.LINE);
                    if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                        g.fillRect(getWidth() / 2, 0, 1, getHeight());
                    } else {
                        g.fillRect(0, getHeight() / 2, getWidth(), 1);
                    }
                }

                @Override
                public void setBorder(javax.swing.border.Border border) {
                    super.setBorder(null);
                }
            };
        }
    }

    private static final class PlainTabsUI extends BasicTabbedPaneUI {
        /** Matches the editor section header, so the two rules form one line across the window. */
        private static final int TAB_HEIGHT = 36;

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(8, 12, 8, 12);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(0, 0, 0, 0);
            contentBorderInsets = new Insets(1, 0, 0, 0);
        }

        @Override
        protected int calculateTabHeight(int placement, int index, int fontHeight) {
            return TAB_HEIGHT;
        }

        @Override
        protected void paintTabArea(Graphics g, int placement, int selectedIndex) {
            g.setColor(Theme.HEADER);
            g.fillRect(0, 0, tabPane.getWidth(), TAB_HEIGHT);
            super.paintTabArea(g, placement, selectedIndex);
        }

        @Override
        protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
            return 0;
        }

        @Override
        protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
            return 0;
        }

        @Override
        protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h,
                                          boolean isSelected) {
            g.setColor(isSelected ? Color.WHITE : Theme.HEADER);
            g.fillRect(x, y, w, h);
        }

        @Override
        protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h,
                                      boolean isSelected) {
            g.setColor(Theme.LINE);
            g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
            if (isSelected) {
                g.setColor(Theme.BLUE);
                g.fillRect(x, y, w, 2);
            }
        }

        @Override
        protected void paintText(Graphics graphics, int placement, Font font, FontMetrics metrics, int index,
                                 String title, Rectangle textRect, boolean isSelected) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(font);
            g.setColor(isSelected || getRolloverTab() == index ? Theme.TEXT : Theme.MUTED);
            g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
            g.dispose();
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int index,
                                           Rectangle iconRect, Rectangle textRect, boolean isSelected) {
            if (tabPane.hasFocus() && isSelected) {
                g.setColor(Theme.BLUE);
                Rectangle r = rects[index];
                g.drawRect(r.x + 3, r.y + 5, r.width - 8, r.height - 10);
            }
        }

        @Override
        protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
            g.setColor(Theme.LINE);
            int y = calculateTabAreaHeight(placement, runCount, maxTabHeight);
            g.fillRect(0, y, tabPane.getWidth(), 1);
        }
    }
}
