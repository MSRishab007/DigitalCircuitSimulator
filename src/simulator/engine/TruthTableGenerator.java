package simulator.engine;

import simulator.core.VoltageState;
import simulator.parser.NetlistParser;

import java.util.ArrayList;
import java.util.List;

public final class TruthTableGenerator {
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
        List<TruthTable.Row> rows = new ArrayList<>();
        int combinations = 1 << inputNames.size();
        for (int mask = 0; mask < combinations; mask++) {
            SimulationContext context = new SimulationContext();
            NetlistParser parser = new NetlistParser(context);
            parser.parseString(removeStimuli(netlist));
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

    private static String removeStimuli(String netlist) {
        StringBuilder result = new StringBuilder();
        for (String line : netlist.split("\\R")) {
            if (!line.trim().toUpperCase().startsWith("STIMULUS ")) {
                result.append(line).append('\n');
            }
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
