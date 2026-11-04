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

    /** Renders the table with each value lined up under its wire name. */
    public String toText() {
        StringBuilder text = new StringBuilder();
        appendColumns(text, inputs, inputs);
        text.append(" | ");
        appendColumns(text, outputs, outputs);
        text.append('\n');
        for (Row row : rows) {
            appendColumns(text, row.inputStates, inputs);
            text.append(" | ");
            appendColumns(text, row.outputStates, outputs);
            text.append('\n');
        }
        return text.toString();
    }

    public String toCsv() {
        List<String> header = new ArrayList<>(inputs);
        header.addAll(outputs);
        StringBuilder csv = new StringBuilder(String.join(",", header)).append('\n');
        for (Row row : rows) {
            List<String> values = new ArrayList<>(row.inputStates);
            values.addAll(row.outputStates);
            csv.append(String.join(",", values)).append('\n');
        }
        return csv.toString();
    }

    private static void appendColumns(StringBuilder text, List<String> values, List<String> headers) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) text.append(' ');
            text.append(values.get(i));
            text.append(" ".repeat(headers.get(i).length() - values.get(i).length()));
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
