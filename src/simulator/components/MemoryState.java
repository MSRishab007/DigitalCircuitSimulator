package simulator.components;

import simulator.core.VoltageState;

public interface MemoryState {
    MemoryState handleClockPulse(VoltageState dataInput);
    
    VoltageState getOutputVoltage();
}