package simulator;

import simulator.application.SimulationApplicationService;
import simulator.parser.SimulationFacade;
import simulator.engine.CircuitPorts;
import simulator.engine.TruthTable;

import java.util.List;

public class Main {
    private static final SimulationApplicationService SERVICE =
            new SimulationApplicationService();
    private static final java.util.Set<String> COMMANDS =
            java.util.Set.of("simulate", "validate", "analyze", "truth-table", "equivalent");

    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) {
            System.exit(status);
        }
    }

    /** Runs one command and returns its exit status: 0 for success, 2 for circuits that differ. */
    static int run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return 1;
        }
        boolean named = COMMANDS.contains(args[0].toLowerCase());
        String command = named ? args[0].toLowerCase() : "simulate";
        String[] rest = java.util.Arrays.copyOfRange(args, named ? 1 : 0, args.length);
        if (!hasValidArguments(command, rest.length)) {
            printUsage();
            return 1;
        }

        try {
            switch (command) {
                case "simulate" -> new SimulationFacade().executeSimulation(
                        rest[0], rest.length > 1 ? rest[1] : "output.vcd");
                case "validate" -> validate(rest[0]);
                case "analyze" -> analyze(rest[0]);
                case "truth-table" -> truthTable(rest);
                default -> {
                    return equivalent(rest);
                }
            }
            return 0;
        } catch (java.nio.file.NoSuchFileException e) {
            System.err.println("\n[!] " + command.toUpperCase() + " FAILED: File not found: " + e.getFile());
            return 1;
        } catch (Exception e) {
            System.err.println("\n[!] " + command.toUpperCase() + " FAILED: " + e.getMessage());
            return 1;
        }
    }

    private static boolean hasValidArguments(String command, int count) {
        return switch (command) {
            case "simulate" -> count == 1 || count == 2;
            case "validate", "analyze" -> count == 1;
            case "truth-table" -> count == 1 || count == 3;
            default -> count == 2 || count == 4;
        };
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

    private static void truthTable(String[] args) throws Exception {
        String netlist = java.nio.file.Files.readString(java.nio.file.Path.of(args[0]));
        List<List<String>> wires = chooseWires(netlist, args, 1);
        TruthTable table = SERVICE.truthTable(netlist, wires.get(0), wires.get(1));
        System.out.print(table.toText());
    }

    private static int equivalent(String[] args) throws Exception {
        String first = java.nio.file.Files.readString(java.nio.file.Path.of(args[0]));
        String second = java.nio.file.Files.readString(java.nio.file.Path.of(args[1]));
        List<List<String>> wires = chooseWires(first, args, 2);
        var result = SERVICE.equivalent(first, second, wires.get(0), wires.get(1));
        if (result.isEquivalent()) {
            System.out.println("EQUIVALENT");
            return 0;
        }
        System.out.println("NOT_EQUIVALENT");
        System.out.println("COUNTEREXAMPLE_ROW=" + result.getCounterexampleRow());
        System.out.println("INPUTS=" + String.join(" ", result.getFirstRow().getInputStates()));
        System.out.println("FIRST_OUTPUTS=" + String.join(" ", result.getFirstRow().getOutputStates()));
        System.out.println("SECOND_OUTPUTS=" + String.join(" ", result.getSecondRow().getOutputStates()));
        return 2;
    }

    /** Uses the wires named on the command line, or detects them when none are given. */
    private static List<List<String>> chooseWires(String netlist, String[] args, int firstWireArgument) {
        if (args.length > firstWireArgument) {
            return List.of(names(args[firstWireArgument]), names(args[firstWireArgument + 1]));
        }
        CircuitPorts ports = SERVICE.detectPorts(netlist);
        if (ports.getInputs().isEmpty() || ports.getOutputs().isEmpty()) {
            throw new IllegalArgumentException("Could not detect input and output wires. "
                    + "Name them on the command line. Wires in this netlist: "
                    + String.join(", ", ports.getWires()));
        }
        return List.of(ports.getInputs(), ports.getOutputs());
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
        System.err.println("  java simulator.Main truth-table <netlist> [<inputs> <outputs>]");
        System.err.println("  java simulator.Main equivalent <first> <second> [<inputs> <outputs>]");
        System.err.println("  java simulator.Main <netlist> [output.vcd]");
        System.err.println("Inputs and outputs are comma separated. Without them, wires are detected from the netlist.");
    }
}