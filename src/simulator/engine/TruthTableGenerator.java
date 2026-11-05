package simulator.engine;

import simulator.core.VoltageState;
import simulator.parser.NetlistParser;
import simulator.parser.TopologyValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class TruthTableGenerator {
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
        if (inputNames == null || outputNames == null) {
            throw new IllegalArgumentException("Truth tables require input and output wires");
        }
        String circuit = removeTestbench(netlist);
        CircuitPorts ports = validate(circuit, inputNames, outputNames);
        List<String> state = ports.getStateWires();
        List<String> outputs = new ArrayList<>();
        for (String output : outputNames) {
            if (!state.contains(output)) {
                outputs.add(output);
            }
        }
        if (state.isEmpty() && (inputNames.isEmpty() || outputs.isEmpty())) {
            throw new IllegalArgumentException("Truth tables require input and output wires");
        }
        List<String> driven = new ArrayList<>(inputNames);
        driven.addAll(state);
        if (driven.size() > maximumInputs) {
            throw new IllegalArgumentException("Truth table input count exceeds configured limit");
        }

        List<TruthTable.Row> rows = new ArrayList<>();
        int combinations = 1 << driven.size();
        for (int mask = 0; mask < combinations; mask++) {
            SimulationContext context = new SimulationContext();
            NetlistParser parser = new NetlistParser(context);
            parser.parseString(circuit);
            context.getEventEngine().setMaxSimulationTime(Long.MAX_VALUE);
            List<String> inputStates = new ArrayList<>();
            for (int bit = 0; bit < driven.size(); bit++) {
                VoltageState value = ((mask >> (driven.size() - bit - 1)) & 1) == 1
                        ? VoltageState.HIGH : VoltageState.LOW;
                inputStates.add(format(value));
                if (bit >= inputNames.size()) {
                    parser.getWire(driven.get(bit)).injectStuckAtFault(value);
                } else {
                    context.getEventEngine().scheduleEvent(
                            new SignalEvent(0, parser.getWire(driven.get(bit)), value));
                }
            }
            context.runSimulation();
            List<String> outputStates = new ArrayList<>();
            for (String next : ports.getNextStateWires()) {
                outputStates.add(format(parser.getWire(next).getCurrentState()));
            }
            for (String outputName : outputs) {
                outputStates.add(format(parser.getWire(outputName).getCurrentState()));
            }
            rows.add(new TruthTable.Row(inputStates, outputStates));
        }
        List<String> columns = new ArrayList<>();
        for (String wire : state) {
            columns.add(wire + "+");
        }
        columns.addAll(outputs);
        return new TruthTable(driven, columns, rows);
    }

    private static CircuitPorts validate(String circuit, List<String> inputNames, List<String> outputNames) {
        NetlistParser parser = new NetlistParser(new SimulationContext());
        parser.parseString(circuit);
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
            if (ports.getClockWires().contains(name)) {
                throw new IllegalArgumentException(name + " is a clock wire, so it cannot be a table input. "
                        + "A state table already shows the result of one clock edge.");
            }
        }
        return ports;
    }

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
