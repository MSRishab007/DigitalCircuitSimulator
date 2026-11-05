package simulator.components;

import simulator.core.VoltageState;

public class LowState implements MemoryState {
    
    @Override
    public MemoryState handleClockPulse(VoltageState dataInput) {
        if (dataInput == VoltageState.HIGH) {
            return new HighState();
        }
        if (dataInput == VoltageState.UNKNOWN || dataInput == VoltageState.HIGH_Z) {
            return new UnknownState();
        }
        return this;
    }

    @Override
    public VoltageState getOutputVoltage() {
        return VoltageState.LOW;
    }
}