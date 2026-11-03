package simulator;

import simulator.components.Clock;
import simulator.core.DigitalComponent;
import simulator.engine.EventEngine;
import simulator.engine.StaticTimingAnalyzer;
import simulator.io.VcdLogger;
import simulator.parser.NetlistParser;
import simulator.parser.TopologyValidator;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java simulator.Main <path_to_netlist>");
            System.exit(1);
        }

        try {
            System.out.println("Loading HDL Netlist: " + args[0] + "...");
            NetlistParser parser = new NetlistParser();
            parser.parse(args[0]);

            TopologyValidator validator = new TopologyValidator();
            if (!validator.validateNoCombinationalLoops(parser.getComponents())) {
                System.err.println("FATAL: Unclocked combinational loop detected.");
                System.exit(1);
            }

            // Run Static Timing Analysis
            StaticTimingAnalyzer sta = new StaticTimingAnalyzer();
            sta.analyze(parser.getComponents());

            VcdLogger logger = new VcdLogger("output.vcd");
            for (var wire : parser.getAllWires()) {
                logger.monitorWire(wire);
            }

            for (DigitalComponent component : parser.getComponents()) {
                if (component instanceof Clock) ((Clock) component).generateClockPulses();
            }

            EventEngine engine = EventEngine.getInstance();
            engine.runSimulation();
            logger.exportFile();

            System.out.println("\n=================================================");
            System.out.println("[+] SIMULATION TELEMETRY REPORT");
            System.out.println("=================================================");
            System.out.println(String.format("%-25s %d nodes", "Circuit Topology:", parser.getAllWires().size() + parser.getComponents().size()));
            System.out.println(String.format("%-25s %d ns", "Max Simulated Time:", engine.getMaxSimulationTime()));
            System.out.println(String.format("%-25s %d events", "Total Events Processed:", engine.getTotalEventsProcessed()));
            System.out.println(String.format("%-25s %d", "Peak Queue Depth:", engine.getPeakQueueDepth()));
            System.out.println(String.format("%-25s %d ms", "CPU Compute Time:", engine.getComputeTimeMillis()));
            System.out.println(String.format("%-25s %s", "Waveform Exported:", "output.vcd"));
            System.out.println("=================================================\n");

        } catch (Exception e) {
            System.err.println("\n[!] SIMULATION FAILED: " + e.getMessage());
        }
    }
}