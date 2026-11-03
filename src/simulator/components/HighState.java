package simulator.components;

import simulator.core.VoltageState;

public class HighState implements MemoryState {
    
    @Override
    public MemoryState handleClockPulse(VoltageState dataInput) {
        // Example logic for a D Flip-Flop: Next state equals Data Input
        if (dataInput == VoltageState.LOW) {
            return new LowState(); // Transition to LowState
        }
        return this; // Remain in HighState
    }

    @Override
    public VoltageState getOutputVoltage() {
        return VoltageState.HIGH;
    }
}