package simulator.components;

import simulator.core.VoltageState;

public class OrGate extends LogicGate {

    public OrGate(String id, int propagationDelay) {
        super(id, 2, 1, propagationDelay);
    }

    @Override
    protected VoltageState calculateBooleanLogic() {
        VoltageState input1 = inputPins.get(0).getCurrentState();
        VoltageState input2 = inputPins.get(1).getCurrentState();

        if (input1 == VoltageState.HIGH || input2 == VoltageState.HIGH) {
            return VoltageState.HIGH;
        }
        if (input1 == VoltageState.LOW && input2 == VoltageState.LOW) {
            return VoltageState.LOW;
        }
        return VoltageState.UNKNOWN;
    }
}