package simulator.components;

import simulator.core.VoltageState;

public class AndGate extends LogicGate {

    public AndGate(String id, int propagationDelay) {
        super(id, 2, 1, propagationDelay);
    }

    @Override
    protected VoltageState calculateBooleanLogic() {
        VoltageState input1 = inputPins.get(0).getCurrentState();
        VoltageState input2 = inputPins.get(1).getCurrentState();

        if (input1 == VoltageState.LOW || input2 == VoltageState.LOW) {
            return VoltageState.LOW;
        }
        if (input1 == VoltageState.HIGH && input2 == VoltageState.HIGH) {
            return VoltageState.HIGH;
        }
        return VoltageState.UNKNOWN;
    }
}