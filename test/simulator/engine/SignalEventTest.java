package simulator.engine;

import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.core.Wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SignalEventTest {
    @Test
    void exposesScheduledSignalDetails() {
        Wire wire = new Wire("W");
        Object driver = new Object();
        SignalEvent event = new SignalEvent(42, wire, VoltageState.HIGH, driver);

        assertEquals(42, event.getExecutionTime());
        assertSame(wire, event.getTargetWire());
        assertEquals(VoltageState.HIGH, event.getNewState());
        assertSame(driver, event.getDriver());
    }

    @Test
    void ordersEventsByExecutionTime() {
        Wire wire = new Wire("W");
        SignalEvent earlier = new SignalEvent(1, wire, VoltageState.LOW);
        SignalEvent later = new SignalEvent(2, wire, VoltageState.HIGH);

        org.junit.jupiter.api.Assertions.assertTrue(earlier.compareTo(later) < 0);
        org.junit.jupiter.api.Assertions.assertTrue(later.compareTo(earlier) > 0);
    }
}
