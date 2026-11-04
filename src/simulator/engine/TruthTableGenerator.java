package simulator.engine;

import simulator.components.Clock;
import simulator.components.SequentialComponent;
import simulator.core.DigitalComponent;
import simulator.core.VoltageState;
import simulator.parser.NetlistParser;
import simulator.parser.TopologyValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class TruthTableGenerator {
    /** Testbench lines describe one run, so they do not apply when every input combination is tried. */
    private static final List<String> TESTBENCH_COMMANDS = List.of("STIMULUS", "ASSERT", "SIM_TIME");

    private final int maximumInputs;

    public TruthTableGenerator() {
        this(16);
    }

    public TruthTableGenerator(int maximumInputs) {
        if (maximumInputs < 1 || maximumInputs > 20) {
            throw new IllegalArgumentException("Maximum inputs must be between 1 and 20");
        }
        this.maximumInputs = maximumInputs;
    }

    public TruthTable generate(String netlist, List<String> inputNames,
                               List<String> outputNames) {
        if (inputNames == null || outputNames == null || inputNames.isEmpty()
                || outputNames.isEmpty()) {
            throw new IllegalArgumentException("Truth tables require input and output wires");
        }
        if (inputNames.size() > maximumInputs) {
            throw new IllegalArgumentException("Truth table input count exceeds configured limit");
        }
        String circuit = removeTestbench(netlist);
        validate(circuit, inputNames, outputNames);

        List<TruthTable.Row> rows = new ArrayList<>();
        int combinations = 1 << inputNames.size();
        for (int mask = 0; mask < combinations; mask++) {
            SimulationContext context = new SimulationContext();
            NetlistParser parser = new NetlistParser(context);
            parser.parseString(circuit);
            // validate() rules out clocks and loops, so the circuit settles and needs no time limit.
            context.getEventEngine().setMaxSimulationTime(Long.MAX_VALUE);
            List<String> inputStates = new ArrayList<>();
            for (int bit = 0; bit < inputNames.size(); bit++) {
                VoltageState state = ((mask >> (inputNames.size() - bit - 1)) & 1) == 1
                        ? VoltageState.HIGH : VoltageState.LOW;
                inputStates.add(format(state));
                context.getEventEngine().scheduleEvent(
                        new SignalEvent(0, parser.getWire(inputNames.get(bit)), state));
            }
            context.runSimulation();
            List<String> outputStates = new ArrayList<>();
            for (String outputName : outputNames) {
                outputStates.add(format(parser.getWire(outputName).getCurrentState()));
            }
            rows.add(new TruthTable.Row(inputStates, outputStates));
        }
        return new TruthTable(inputNames, outputNames, rows);
    }

    private static void validate(String circuit, List<String> inputNames, List<String> outputNames) {
        NetlistParser parser = new NetlistParser(new SimulationContext());
        parser.parseString(circuit);
        for (DigitalComponent component : parser.getComponents()) {
            if (component instanceof Clock || component instanceof SequentialComponent) {
                throw new IllegalArgumentException("Truth tables only work on combinational circuits, but "
                        + component.getComponentId() + " is a clock or flip-flop. "
                        + "Run a simulation and read the waveform instead.");
            }
        }
        if (!new TopologyValidator().validateNoCombinationalLoops(parser.getComponents())) {
            throw new IllegalArgumentException("Truth tables cannot be generated for a circuit "
                    + "with a combinational loop");
        }
        CircuitPorts ports = new CircuitPorts(parser);
        for (String name : outputNames) {
            parser.getWire(name);
        }
        for (String name : inputNames) {
            parser.getWire(name);
            if (ports.getDriver(name) != null) {
                throw new IllegalArgumentException("Input wire " + name + " is already driven by "
                        + ports.getDriver(name) + ". Choose wires that no gate drives.");
            }
        }
    }

    /**
     * Blanks testbench lines instead of removing them, so error line numbers still match the netlist.
     * Reads lines with BufferedReader because the browser build's regex engine rejects the \\R pattern.
     */
    private static String removeTestbench(String netlist) {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(netlist))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String command = line.trim().split("\\s+")[0].toUpperCase();
                if (!TESTBENCH_COMMANDS.contains(command)) {
                    result.append(line);
                }
                result.append('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading netlist", e);
        }
        return result.toString();
    }

    private static String format(VoltageState state) {
        return switch (state) {
            case HIGH -> "1";
            case LOW -> "0";
            case HIGH_Z -> "Z";
            case UNKNOWN -> "X";
        };
    }
}
