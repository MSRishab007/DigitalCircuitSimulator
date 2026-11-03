package simulator.engine;

import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.core.Wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationDebuggerTest {
    @Test
    void stepsEventsAndExposesQueueState() {
        SimulationContext context = new SimulationContext();
        Wire wire = new Wire("A");
        context.getEventEngine().scheduleEvent(
                new SignalEvent(5, wire, VoltageState.HIGH));
        SimulationDebugger debugger = new SimulationDebugger(context);

        assertEquals(1, debugger.getPendingEventCount());
        assertTrue(debugger.step());
        assertEquals(5, debugger.getCurrentTime());
        assertEquals(VoltageState.HIGH, wire.getCurrentState());
        assertTrue(debugger.isComplete());
    }
}
