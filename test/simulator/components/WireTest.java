package simulator.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WireTest {
    private Wire testWire;

    @BeforeEach
    void setUp() {
        testWire = new Wire("TestBus");
    }

    @Test
    void testSequentialStateUpdates() {
        testWire.updateVoltage(VoltageState.HIGH);
        assertEquals(VoltageState.HIGH, testWire.getCurrentState());
        
        testWire.updateVoltage(VoltageState.LOW);
        assertEquals(VoltageState.LOW, testWire.getCurrentState(), 
                "Wire must allow sequential transitions over time.");
    }

    @Test
    void testStuckAtFaultBlocksNormalUpdates() {
        testWire.injectStuckAtFault(VoltageState.LOW); 
        testWire.updateVoltage(VoltageState.HIGH); 
        
        assertEquals(VoltageState.LOW, testWire.getCurrentState(), 
                "Wire must remain LOW because the physical fault overrides logic");
    }
}