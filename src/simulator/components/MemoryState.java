package simulator.components;

import simulator.core.VoltageState;

public interface MemoryState {
    // Defines how the state reacts to a clock pulse
    MemoryState handleClockPulse(VoltageState dataInput);
    
    // Defines what electrical signal the state outputs
    VoltageState getOutputVoltage();
}