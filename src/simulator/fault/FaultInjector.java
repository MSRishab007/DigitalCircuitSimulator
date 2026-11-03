package simulator.fault;

import simulator.core.VoltageState;
import simulator.core.Wire;

public class FaultInjector {
    
    public static void stuckAtZero(Wire targetWire) {
        targetWire.injectStuckAtFault(VoltageState.LOW);
    }
    
    public static void stuckAtOne(Wire targetWire) {
        targetWire.injectStuckAtFault(VoltageState.HIGH);
    }
}