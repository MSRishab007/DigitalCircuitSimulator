package simulator.components;

import simulator.core.VoltageState;

public class NotGate extends LogicGate {

    public NotGate(String id, int propagationDelay) {
        super(id, 1, 1, propagationDelay);
    }

    @Override
    protected VoltageState calculateBooleanLogic() {
        VoltageState input = inputPins.get(0).getCurrentState();

        if (input == VoltageState.HIGH) {
            return VoltageState.LOW;
        }
        if (input == VoltageState.LOW) {
            return VoltageState.HIGH;
        }
        return VoltageState.UNKNOWN;
    }
}