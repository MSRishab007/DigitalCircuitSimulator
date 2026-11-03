package simulator.gui;

import simulator.components.Clock;
import simulator.core.DigitalComponent;
import simulator.engine.EventEngine;
import simulator.engine.StaticTimingAnalyzer;
import simulator.io.VcdLogger;
import simulator.parser.NetlistParser;
import simulator.parser.TopologyValidator;

import javax.swing.*;
import java.awt.*;
import java.io.*;

public class SimulatorDashboard extends JFrame {
    private final JTextArea editorArea;
    private final JTextArea logConsole;
    private final WaveformPanel waveformPanel; 
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
        JButton runBtn = new JButton("Run Simulation");

        toolBar.add(openBtn);
        toolBar.add(saveBtn);
        toolBar.addSeparator();
        toolBar.add(runBtn);

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

        try {
            // Save temporary netlist execution file
            File tempNetlist = File.createTempFile("temp_sim", ".netlist");
            try (BufferedWriter w = new BufferedWriter(new FileWriter(tempNetlist))) {
                editorArea.write(w);
            }

            NetlistParser parser = new NetlistParser();
            parser.parse(tempNetlist.getAbsolutePath());

            TopologyValidator validator = new TopologyValidator();
            if (!validator.validateNoCombinationalLoops(parser.getComponents())) {
                log("FATAL: Unclocked combinational loop detected!");
                return;
            }

            StaticTimingAnalyzer sta = new StaticTimingAnalyzer();
            sta.analyze(parser.getComponents());

            VcdLogger logger = new VcdLogger("output.vcd");
            for (var wire : parser.getAllWires()) {
                logger.monitorWire(wire);
            }

            for (DigitalComponent component : parser.getComponents()) {
                if (component instanceof Clock) {
                    ((Clock) component).generateClockPulses();
                }
            }

            EventEngine engine = EventEngine.getInstance();
            engine.runSimulation();
            engine.runSimulation();
            logger.exportFile();
            waveformPanel.loadVcd("output.vcd");
            

            log("\n===============================================");
            log("[+] SIMULATION TELEMETRY REPORT");
            log("===============================================");
            log(String.format("%-25s %d nodes", "Circuit Topology:", parser.getAllWires().size() + parser.getComponents().size()));
            log(String.format("%-25s %d ns", "Max Simulated Time:", engine.getMaxSimulationTime()));
            log(String.format("%-25s %d events", "Total Events Processed:", engine.getTotalEventsProcessed()));
            log(String.format("%-25s %d", "Peak Queue Depth:", engine.getPeakQueueDepth()));
            log(String.format("%-25s %d ms", "CPU Compute Time:", engine.getComputeTimeMillis()));
            log(String.format("%-25s %s", "Waveform Exported:", "output.vcd"));
            log("===============================================");

            tempNetlist.deleteOnExit();

        } catch (Exception ex) {
            log("\n[!] SIMULATION FAILED: " + ex.getMessage());
        }
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