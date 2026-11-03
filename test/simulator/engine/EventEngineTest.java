package simulator.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.core.Wire;

import static org.junit.jupiter.api.Assertions.*;

class EventEngineTest {

    private EventEngine engine;

    @BeforeEach
    void setUp() {
        // Ensures a perfectly clean state before every single test
        engine = EventEngine.getInstance();
        engine.resetEngine();
    }

    @Test
    void testSingletonInstance() {
        EventEngine engine2 = EventEngine.getInstance();
        assertSame(engine, engine2, "EventEngine must be a strict Singleton");
    }

    @Test
    void testEventChronologicalOrdering() {
        Wire testWire = new Wire("TestWire");
        
        // Schedule events out of chronological order
        engine.scheduleEvent(new SignalEvent(50, testWire, VoltageState.HIGH));
        engine.scheduleEvent(new SignalEvent(10, testWire, VoltageState.LOW));
        engine.scheduleEvent(new SignalEvent(30, testWire, VoltageState.HIGH_Z));

        engine.runSimulation();

        // The final state applied should be HIGH at 50ns, 
        // proving the queue sorted them: 10ns -> 30ns -> 50ns
        assertEquals(VoltageState.HIGH, testWire.getCurrentState(), 
                "Wire should end in the state of the final chronological event");
        assertEquals(50, engine.getCurrentTime(), 
                "Master clock should advance to the timestamp of the last event");
    }
}
