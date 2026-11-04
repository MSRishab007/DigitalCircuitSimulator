package simulator.engine;

import simulator.components.Clock;
import simulator.components.SequentialComponent;
import simulator.core.DigitalComponent;
import simulator.core.VoltageState;
import simulator.parser.NetlistParser;
import simulator.parser.TopologyValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TruthTableGenerator {
    /** Testbench lines describe one run, so they do not apply when every input combination is tried. */
    private static final Pattern TESTBENCH_LINE =
            Pattern.compile("^\\s*(STIMULUS|ASSERT|SIM_TIME)\\s.*$", Pattern.CASE_INSENSITIVE);

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

    /** Blanks testbench lines instead of removing them, so error line numbers still match the netlist. */
    private static String removeTestbench(String netlist) {
        StringBuilder result = new StringBuilder();
        for (String line : netlist.split("\\R", -1)) {
            if (!TESTBENCH_LINE.matcher(line).matches()) {
                result.append(line);
            }
            result.append('\n');
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
