package simulator.gui;

import simulator.components.Clock;
import simulator.application.SimulationApplicationService;
import simulator.application.SimulationApplicationService;
import simulator.core.DigitalComponent;
import simulator.engine.CircuitEquivalenceChecker;
import simulator.engine.TimingAnalysisResult;
import simulator.engine.TruthTable;
import simulator.engine.SimulationContext;
import simulator.engine.SimulationDebugger;
import simulator.io.VcdLogger;
import simulator.parser.NetlistParser;
import simulator.parser.TopologyValidator;
import simulator.parser.SimulationRunner;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Arrays;
import java.util.List;

public class SimulatorDashboard extends JFrame {
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
    public SimulatorDashboard() {
        setTitle("EDA Digital Circuit Simulator - Dashboard");
        setSize(1024, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // --- Toolbar ---
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton openBtn = new JButton("Open Netlist");
        JButton saveBtn = new JButton("Save Netlist");
        runBtn = new JButton("Run Simulation");
        stepBtn = new JButton("Step Event");
        resetBtn = new JButton("Reset");
        statusLabel = new JLabel("No simulation loaded");

        toolBar.add(openBtn);
        toolBar.add(saveBtn);
        toolBar.addSeparator();
        toolBar.add(runBtn);
        toolBar.add(stepBtn);
        toolBar.add(resetBtn);
        toolBar.addSeparator();
        toolBar.add(statusLabel);

        // --- Editor Pane (Left) ---
        editorArea = new JTextArea();
        editorArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        editorArea.setTabSize(4);
        editorArea.setText(getDefaultNetlistSample());
        JScrollPane editorScroll = new JScrollPane(editorArea);
        editorScroll.setBorder(BorderFactory.createTitledBorder("HDL Netlist Editor"));
// --- Tabbed Pane (Right Side) ---
        JTabbedPane rightTabs = new JTabbedPane();
        
        // Tab 1: Console
        logConsole = new JTextArea();
        logConsole.setFont(new Font("Monospaced", Font.PLAIN, 13));
        logConsole.setEditable(false);
        logConsole.setBackground(new Color(25, 25, 25));
        logConsole.setForeground(new Color(0, 255, 120));
        JScrollPane logScroll = new JScrollPane(logConsole);
        rightTabs.addTab("Telemetry Log", logScroll);

        // Tab 2: Waveform Viewer
        waveformPanel = new WaveformPanel();
        JScrollPane waveScroll = new JScrollPane(waveformPanel);
        waveScroll.getVerticalScrollBar().setUnitIncrement(16);
        rightTabs.addTab("Waveform Viewer", waveScroll);

        rightTabs.addTab("Analysis", createAnalysisPanel());

        // --- Split Pane Layout ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScroll, rightTabs);
        splitPane.setDividerLocation(400);

        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        // --- Action Listeners ---
        openBtn.addActionListener(e -> openFile());
        saveBtn.addActionListener(e -> saveFile());
        runBtn.addActionListener(e -> runSimulation());
        stepBtn.addActionListener(e -> stepSimulation());
        resetBtn.addActionListener(e -> resetSimulation());
        stepBtn.setEnabled(false);
        resetBtn.setEnabled(false);
    }

    private JPanel createAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new GridLayout(3, 2, 5, 5));
        JTextField inputsField = new JTextField("Input_A,Input_B,Input_Cin");
        JTextField outputsField = new JTextField("Sum,CarryOut");
        JButton timingButton = new JButton("Analyze Timing");
        JButton truthTableButton = new JButton("Generate Truth Table");
        controls.setBorder(BorderFactory.createTitledBorder("Current Netlist"));
        controls.add(new JLabel("Input wires:"));
        controls.add(inputsField);
        controls.add(new JLabel("Output wires:"));
        controls.add(outputsField);
        controls.add(timingButton);
        controls.add(truthTableButton);

        JTextArea comparisonArea = new JTextArea();
        comparisonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        comparisonArea.setText("# Paste a second netlist here for equivalence checking\n");
        JScrollPane comparisonScroll = new JScrollPane(comparisonArea);
        comparisonScroll.setBorder(BorderFactory.createTitledBorder("Comparison Netlist"));
        JButton equivalenceButton = new JButton("Check Equivalence");
        JPanel comparisonPanel = new JPanel(new BorderLayout(5, 5));
        comparisonPanel.add(comparisonScroll, BorderLayout.CENTER);
        comparisonPanel.add(equivalenceButton, BorderLayout.SOUTH);

        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Analysis Result"));

        timingButton.addActionListener(event -> {
            try {
                TimingAnalysisResult result = service.analyze(editorArea.getText());
                resultArea.setText("Critical path delay: "
                        + result.getCriticalPathDelay() + " ns\n"
                        + "Maximum clock frequency: "
                        + result.getMaxClockFrequencyMHz() + " MHz");
            } catch (Exception exception) {
                resultArea.setText("Analysis failed: " + exception.getMessage());
            }
        });
        truthTableButton.addActionListener(event -> {
            try {
                TruthTable table = service.truthTable(editorArea.getText(),
                        names(inputsField.getText()), names(outputsField.getText()));
                resultArea.setText(table.toText());
            } catch (Exception exception) {
                resultArea.setText("Truth-table generation failed: "
                        + exception.getMessage());
            }
        });
        equivalenceButton.addActionListener(event -> {
            try {
                CircuitEquivalenceChecker.EquivalenceResult result =
                        service.equivalent(editorArea.getText(), comparisonArea.getText(),
                                names(inputsField.getText()), names(outputsField.getText()));
                if (result.isEquivalent()) {
                    resultArea.setText("Equivalent for all input combinations.");
                } else {
                    resultArea.setText("Not equivalent.\nCounterexample row: "
                            + result.getCounterexampleRow() + "\nInputs: "
                            + result.getFirstRow().getInputStates() + "\nCurrent outputs: "
                            + result.getFirstRow().getOutputStates() + "\nComparison outputs: "
                            + result.getSecondRow().getOutputStates());
                }
            } catch (Exception exception) {
                resultArea.setText("Equivalence check failed: "
                        + exception.getMessage());
            }
        });

        panel.add(controls, BorderLayout.NORTH);
        panel.add(comparisonPanel, BorderLayout.CENTER);
        panel.add(resultScroll, BorderLayout.SOUTH);
        return panel;
    }

    private static List<String> names(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser(".");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = chooser.getSelectedFile();
            try (BufferedReader r = new BufferedReader(new FileReader(currentFile))) {
                editorArea.read(r, null);
                log("Loaded file: " + currentFile.getAbsolutePath());
            } catch (IOException ex) {
                log("Error reading file: " + ex.getMessage());
            }
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            JFileChooser chooser = new JFileChooser(".");
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                currentFile = chooser.getSelectedFile();
            } else {
                return;
            }
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(currentFile))) {
            editorArea.write(w);
            log("Saved: " + currentFile.getAbsolutePath());
        } catch (IOException ex) {
            log("Error saving file: " + ex.getMessage());
        }
    }

    private void runSimulation() {
        logConsole.setText("");
        log("===============================================");
        log("[+] INITIALIZING SIMULATION");
        log("===============================================");
        final String netlist = editorArea.getText();
        final String waveformPath = new File("output.vcd").getAbsolutePath();
        new SwingWorker<SimulationRunner.SimulationResult, Void>() {
            @Override
            protected SimulationRunner.SimulationResult doInBackground() {
                return service.simulate(netlist, waveformPath);
            }

            @Override
            protected void done() {
                try {
                    SimulationRunner.SimulationResult result = get();
                    waveformPanel.loadVcd(waveformPath);
                    log("\n===============================================");
                    log("[+] SIMULATION TELEMETRY REPORT");
                    log("===============================================");
                    log(String.format("%-25s %d nodes", "Circuit Topology:",
                            result.getWireCount() + result.getComponentCount()));
                    log(String.format("%-25s %d events", "Total Events Processed:",
                            result.getEventsProcessed()));
                    log(String.format("%-25s %d", "Peak Queue Depth:",
                            result.getPeakQueueDepth()));
                    log(String.format("%-25s %d ms", "CPU Compute Time:",
                            result.getComputeTimeMillis()));
                    log(String.format("%-25s %s", "Waveform Exported:", waveformPath));
                    log("===============================================");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    log("\n[!] SIMULATION FAILED: " + cause.getMessage());
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
            }
            if (!debugger.step()) {
                log("Simulation complete.");
                stepBtn.setEnabled(false);
            }
            exportDebugWaveform();
            updateDebugStatus();
        } catch (Exception ex) {
            log("[!] STEP FAILED: " + ex.getMessage());
        }
    }

    private void resetSimulation() {
        try {
            prepareDebugSession();
            log("Simulation reset.");
        } catch (Exception ex) {
            log("[!] RESET FAILED: " + ex.getMessage());
        }
    }

    private void exportDebugWaveform() throws IOException {
        if (debugLogger != null) {
            debugLogger.exportFile();
            waveformPanel.loadVcd(debugWaveformPath);
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
        SwingUtilities.invokeLater(() -> {
            new SimulatorDashboard().setVisible(true);
        });
    }
}