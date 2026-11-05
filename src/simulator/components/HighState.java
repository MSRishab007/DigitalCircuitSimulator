package simulator.components;

import simulator.core.VoltageState;

public class HighState implements MemoryState {
    
    @Override
    public MemoryState handleClockPulse(VoltageState dataInput) {
        if (dataInput == VoltageState.LOW) {
            return new LowState();
        }
        if (dataInput == VoltageState.UNKNOWN || dataInput == VoltageState.HIGH_Z) {
            return new UnknownState();
        }
        return this;
    }

    @Override
    public VoltageState getOutputVoltage() {
        return VoltageState.HIGH;
    }
}