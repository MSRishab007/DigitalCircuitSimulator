package simulator.components;

import simulator.core.VoltageState;

public class TriStateBuffer extends LogicGate {

    public TriStateBuffer(String id, int propagationDelay) {
        super(id, 2, 1, propagationDelay);
    }

    @Override
    protected VoltageState calculateBooleanLogic() {
        VoltageState data = inputPins.get(0).getCurrentState();
        VoltageState enable = inputPins.get(1).getCurrentState();

        if (enable == VoltageState.HIGH) {
            return data;
        } else if (enable == VoltageState.LOW) {
            return VoltageState.HIGH_Z;
        }
        return VoltageState.UNKNOWN;
    }
}