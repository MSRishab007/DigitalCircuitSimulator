package simulator.components;

import simulator.core.VoltageState;

public class LowState implements MemoryState {
    
    @Override
    public MemoryState handleClockPulse(VoltageState dataInput) {
        if (dataInput == VoltageState.HIGH) {
            return new HighState(); // Transition to HighState
        }
        if (dataInput == VoltageState.UNKNOWN || dataInput == VoltageState.HIGH_Z) {
            return new UnknownState();
        }
        return this; // Remain in LowState
    }

    @Override
    public VoltageState getOutputVoltage() {
        return VoltageState.LOW;
    }
}