package simulator.components;

import simulator.core.VoltageState;

public class LowState implements MemoryState {
    
    @Override
    public MemoryState handleClockPulse(VoltageState dataInput) {
        if (dataInput == VoltageState.HIGH) {
            return new HighState(); // Transition to HighState
        }
        return this; // Remain in LowState
    }

    @Override
    public VoltageState getOutputVoltage() {
        return VoltageState.LOW;
    }
}