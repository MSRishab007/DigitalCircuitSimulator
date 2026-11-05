package simulator.components;

import simulator.core.VoltageState;

public class FunctionalBlock extends LogicGate {
    private final BooleanExpression expression;

    public FunctionalBlock(String id, int delay, int inputCount, BooleanExpression expression) {
        super(id, inputCount, 1, delay);
        this.expression = expression;
    }

    @Override
    protected VoltageState calculateBooleanLogic() {
        VoltageState[] values = new VoltageState[inputPins.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = inputPins.get(i).getCurrentState();
        }
        return expression.evaluate(values);
    }

    public String getExpressionText() {
        return expression.getText();
    }
}
