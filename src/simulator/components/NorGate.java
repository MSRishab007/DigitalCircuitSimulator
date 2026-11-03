package simulator.components;
import simulator.core.VoltageState;

public class NorGate extends LogicGate {
    public NorGate(String id, int delay) {
        super(id, 2, 1, delay);
    }

    @Override
    protected VoltageState calculateBooleanLogic() {
        VoltageState in1 = inputPins.get(0).getCurrentState();
        VoltageState in2 = inputPins.get(1).getCurrentState();

        if (in1 == VoltageState.HIGH || in2 == VoltageState.HIGH) return VoltageState.LOW;
        if (in1 == VoltageState.LOW && in2 == VoltageState.LOW) return VoltageState.HIGH;
        return VoltageState.UNKNOWN;
    }
}