package simulator.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DriverLifecycleTest {
    @Test
    void reconnectingOutputReleasesTheOldWire() {
        OutputPin output = new OutputPin();
        Wire oldWire = new Wire("OLD");
        Wire newWire = new Wire("NEW");

        output.connect(oldWire);
        output.driveSignal(VoltageState.HIGH);
        output.connect(newWire);

        assertEquals(VoltageState.UNKNOWN, oldWire.getCurrentState());
        assertEquals(VoltageState.UNKNOWN, newWire.getCurrentState());
    }

    @Test
    void rejectsNullConnectionsAndEvents() {
        OutputPin output = new OutputPin();
        assertThrows(IllegalArgumentException.class, () -> output.connect(null));
    }
}
