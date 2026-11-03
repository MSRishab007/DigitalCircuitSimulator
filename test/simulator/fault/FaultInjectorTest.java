package simulator.fault;

import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.core.Wire;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FaultInjectorTest {
    @Test
    void injectsStuckAtZero() {
        Wire wire = new Wire("ZERO");
        FaultInjector.stuckAtZero(wire);
        wire.updateVoltage(VoltageState.HIGH);
        assertEquals(VoltageState.LOW, wire.getCurrentState());
    }

    @Test
    void injectsStuckAtOne() {
        Wire wire = new Wire("ONE");
        FaultInjector.stuckAtOne(wire);
        wire.updateVoltage(VoltageState.LOW);
        assertEquals(VoltageState.HIGH, wire.getCurrentState());
    }
}
