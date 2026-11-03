package simulator.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TruthTable {
    private final List<String> inputs;
    private final List<String> outputs;
    private final List<Row> rows;

    public TruthTable(List<String> inputs, List<String> outputs, List<Row> rows) {
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.rows = List.copyOf(rows);
    }

    public List<String> getInputs() { return Collections.unmodifiableList(inputs); }
    public List<String> getOutputs() { return Collections.unmodifiableList(outputs); }
    public List<Row> getRows() { return Collections.unmodifiableList(rows); }

    public String toText() {
        StringBuilder text = new StringBuilder();
        appendColumns(text, inputs);
        text.append(" | ");
        appendColumns(text, outputs);
        text.append('\n');
        for (Row row : rows) {
            appendColumns(text, row.inputStates);
            text.append(" | ");
            appendColumns(text, row.outputStates);
            text.append('\n');
        }
        return text.toString();
    }

    private static void appendColumns(StringBuilder text, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) text.append(' ');
            text.append(values.get(i));
        }
    }

    public static final class Row {
        private final List<String> inputStates;
        private final List<String> outputStates;

        public Row(List<String> inputStates, List<String> outputStates) {
            this.inputStates = List.copyOf(new ArrayList<>(inputStates));
            this.outputStates = List.copyOf(new ArrayList<>(outputStates));
        }

        public List<String> getInputStates() { return inputStates; }
        public List<String> getOutputStates() { return outputStates; }
    }
}
