package simulator;

import simulator.application.SimulationApplicationService;
import simulator.parser.SimulationFacade;
import simulator.engine.CircuitEquivalenceChecker;
import simulator.engine.TruthTable;

public class Main {
    private static final SimulationApplicationService SERVICE =
            new SimulationApplicationService();
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 5) {
            printUsage();
            System.exit(1);
        }

        try {
            String command = args.length > 1 ? args[0].toLowerCase() : "simulate";
            String netlistPath = args.length > 1 ? args[1] : args[0];
            switch (command) {
                case "simulate" -> new SimulationFacade().executeSimulation(
                        netlistPath, args.length > 2 ? args[2] : "output.vcd");
                case "validate" -> validate(netlistPath);
                case "analyze" -> analyze(netlistPath);
                case "truth-table" -> truthTable(netlistPath, args[2], args[3]);
                case "equivalent" -> equivalent(netlistPath, args[2], args[3], args[4]);
                default -> {
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("\n[!] SIMULATION FAILED: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void validate(String path) throws Exception {
        String netlist = java.nio.file.Files.readString(java.nio.file.Path.of(path));
        var result = SERVICE.validate(netlist);
        if (!result.isValid()) throw new IllegalStateException(result.getMessage());
        System.out.println("VALID: " + path);
    }

    private static void analyze(String path) throws Exception {
        String netlist = java.nio.file.Files.readString(java.nio.file.Path.of(path));
        var result = SERVICE.analyze(netlist);
        System.out.println("CRITICAL_PATH_DELAY_NS=" + result.getCriticalPathDelay());
        System.out.println("MAX_CLOCK_FREQUENCY_MHZ=" + result.getMaxClockFrequencyMHz());
    }

    private static void truthTable(String path, String inputs, String outputs) throws Exception {
        String netlist = java.nio.file.Files.readString(java.nio.file.Path.of(path));
        TruthTable table = SERVICE.truthTable(netlist, names(inputs), names(outputs));
        System.out.print(table.toText());
    }

    private static void equivalent(String firstPath, String secondPath,
                                   String inputs, String outputs) throws Exception {
        String first = java.nio.file.Files.readString(java.nio.file.Path.of(firstPath));
        String second = java.nio.file.Files.readString(java.nio.file.Path.of(secondPath));
        var result = SERVICE.equivalent(
                first, second, names(inputs), names(outputs));
        if (result.isEquivalent()) {
            System.out.println("EQUIVALENT");
            return;
        }
        System.out.println("NOT_EQUIVALENT");
        System.out.println("COUNTEREXAMPLE_ROW=" + result.getCounterexampleRow());
        System.out.println("INPUTS=" + String.join(" ", result.getFirstRow().getInputStates()));
        System.out.println("FIRST_OUTPUTS=" + String.join(" ", result.getFirstRow().getOutputStates()));
        System.out.println("SECOND_OUTPUTS=" + String.join(" ", result.getSecondRow().getOutputStates()));
        System.exit(2);
    }

    private static java.util.List<String> names(String value) {
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java simulator.Main simulate <netlist> [output.vcd]");
        System.err.println("  java simulator.Main validate <netlist>");
        System.err.println("  java simulator.Main analyze <netlist>");
        System.err.println("  java simulator.Main truth-table <netlist> <inputs> <outputs>");
        System.err.println("  java simulator.Main equivalent <first> <second> <inputs> <outputs>");
        System.err.println("  java simulator.Main <netlist> [output.vcd]");
    }
}