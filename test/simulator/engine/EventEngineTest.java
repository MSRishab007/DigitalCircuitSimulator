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
        
        engine.scheduleEvent(new SignalEvent(50, testWire, VoltageState.HIGH));
        engine.scheduleEvent(new SignalEvent(10, testWire, VoltageState.LOW));
        engine.scheduleEvent(new SignalEvent(30, testWire, VoltageState.HIGH_Z));

        engine.runSimulation();

        assertEquals(VoltageState.HIGH, testWire.getCurrentState(), 
                "Wire should end in the state of the final chronological event");
        assertEquals(50, engine.getCurrentTime(), 
                "Master clock should advance to the timestamp of the last event");
    }

    @Test
    void doesNotProcessEventsBeyondSimulationLimit() {
        Wire testWire = new Wire("LimitedWire");
        engine.setMaxSimulationTime(20);
        engine.scheduleEvent(new SignalEvent(21, testWire, VoltageState.HIGH));

        engine.runSimulation();

        assertEquals(VoltageState.UNKNOWN, testWire.getCurrentState());
        assertEquals(0, engine.getTotalEventsProcessed());
    }

    @Test
    void rejectsNegativeEventTimes() {
        assertThrows(IllegalArgumentException.class, () ->
                engine.scheduleEvent(new SignalEvent(-1, new Wire("A"), VoltageState.HIGH)));
    }
}
