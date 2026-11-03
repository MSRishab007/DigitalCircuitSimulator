package simulator;

import simulator.parser.SimulationFacade;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java simulator.Main <path_to_netlist> [output_waveform.vcd]");
            System.exit(1);
        }

        try {
            String outputPath = args.length == 2 ? args[1] : "output.vcd";
            new SimulationFacade().executeSimulation(args[0], outputPath);
        } catch (Exception e) {
            System.err.println("\n[!] SIMULATION FAILED: " + e.getMessage());
            System.exit(1);
        }
    }
}