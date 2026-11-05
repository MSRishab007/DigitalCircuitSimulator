package simulator.engine;

import java.util.List;

public final class CircuitEquivalenceChecker {
    public EquivalenceResult compare(String firstNetlist, String secondNetlist,
                                     List<String> inputNames, List<String> outputNames) {
        TruthTable first = new TruthTableGenerator().generate(firstNetlist, inputNames, outputNames);
        TruthTable second = new TruthTableGenerator().generate(secondNetlist, inputNames, outputNames);
        if (!first.getInputs().equals(second.getInputs()) || !first.getOutputs().equals(second.getOutputs())) {
            throw new IllegalArgumentException("These circuits cannot be compared row by row, because their "
                    + "columns differ: " + first.getInputs() + " to " + first.getOutputs() + " and "
                    + second.getInputs() + " to " + second.getOutputs()
                    + ". Sequential circuits must use the same flip-flop output names.");
        }
        for (int i = 0; i < first.getRows().size(); i++) {
            if (!first.getRows().get(i).getOutputStates()
                    .equals(second.getRows().get(i).getOutputStates())) {
                return new EquivalenceResult(false, i, first.getRows().get(i),
                        second.getRows().get(i));
            }
        }
        return new EquivalenceResult(true, -1, null, null);
    }

    public static final class EquivalenceResult {
        private final boolean equivalent;
        private final int counterexampleRow;
        private final TruthTable.Row firstRow;
        private final TruthTable.Row secondRow;

        private EquivalenceResult(boolean equivalent, int counterexampleRow,
                                  TruthTable.Row firstRow, TruthTable.Row secondRow) {
            this.equivalent = equivalent;
            this.counterexampleRow = counterexampleRow;
            this.firstRow = firstRow;
            this.secondRow = secondRow;
        }

        public boolean isEquivalent() { return equivalent; }
        public int getCounterexampleRow() { return counterexampleRow; }
        public TruthTable.Row getFirstRow() { return firstRow; }
        public TruthTable.Row getSecondRow() { return secondRow; }
    }
}
