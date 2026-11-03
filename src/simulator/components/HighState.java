package simulator.components;

import simulator.core.VoltageState;

public class HighState implements MemoryState {
    
    @Override
    public MemoryState handleClockPulse(VoltageState dataInput) {
        // Example logic for a D Flip-Flop: Next state equals Data Input
        if (dataInput == VoltageState.LOW) {
            return new LowState(); // Transition to LowState
        }
        if (dataInput == VoltageState.UNKNOWN || dataInput == VoltageState.HIGH_Z) {
            return new UnknownState();
        }
        return this; // Remain in HighState
    }

    @Override
    public VoltageState getOutputVoltage() {
        return VoltageState.HIGH;
    }
}