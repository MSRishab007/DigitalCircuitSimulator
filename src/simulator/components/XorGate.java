package simulator.components;
import simulator.core.VoltageState;

public class XorGate extends LogicGate {
    public XorGate(String id, int delay) {
        super(id, 2, 1, delay);
    }

    @Override
    protected VoltageState calculateBooleanLogic() {
        VoltageState in1 = inputPins.get(0).getCurrentState();
        VoltageState in2 = inputPins.get(1).getCurrentState();

        if (in1 == VoltageState.UNKNOWN || in1 == VoltageState.HIGH_Z || 
            in2 == VoltageState.UNKNOWN || in2 == VoltageState.HIGH_Z) {
            return VoltageState.UNKNOWN;
        }
        return (in1 != in2) ? VoltageState.HIGH : VoltageState.LOW;
    }
}