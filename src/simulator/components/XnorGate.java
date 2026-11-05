package simulator.components;

import simulator.core.VoltageState;

public class XnorGate extends LogicGate {

    public XnorGate(String id, int propagationDelay) {
        super(id, 2, 1, propagationDelay);
    }

    @Override
    protected VoltageState calculateBooleanLogic() {
        VoltageState input1 = inputPins.get(0).getCurrentState();
        VoltageState input2 = inputPins.get(1).getCurrentState();

        if ((input1 != VoltageState.HIGH && input1 != VoltageState.LOW)
                || (input2 != VoltageState.HIGH && input2 != VoltageState.LOW)) {
            return VoltageState.UNKNOWN;
        }
        return input1 == input2 ? VoltageState.HIGH : VoltageState.LOW;
    }
}
