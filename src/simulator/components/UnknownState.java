package simulator.components;

import simulator.core.VoltageState;

public class UnknownState implements MemoryState {
    @Override
    public MemoryState handleClockPulse(VoltageState dataInput) {
        if (dataInput == VoltageState.HIGH) {
            return new HighState();
        }
        if (dataInput == VoltageState.LOW) {
            return new LowState();
        }
        return this;
    }

    @Override
    public VoltageState getOutputVoltage() {
        return VoltageState.UNKNOWN;
    }
}
