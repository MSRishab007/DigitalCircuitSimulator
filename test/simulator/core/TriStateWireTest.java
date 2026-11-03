package simulator.core;

import org.junit.jupiter.api.Test;

import simulator.components.TriStateBuffer;
import simulator.engine.EventEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriStateWireTest {
    @Test
    void disabledBufferReleasesItsDriver() {
        Wire data = new Wire("DATA");
        Wire enable = new Wire("ENABLE");
        Wire output = new Wire("BUS");
        TriStateBuffer buffer = new TriStateBuffer("BUF", 0);

        buffer.getOutputPin(0).connect(output);
        data.addObserver(buffer.getInputPin(0));
        enable.addObserver(buffer.getInputPin(1));
        data.updateVoltage(VoltageState.HIGH);
        enable.updateVoltage(VoltageState.HIGH);
        enable.updateVoltage(VoltageState.LOW);
        EventEngine.getInstance().runSimulation();

        assertEquals(VoltageState.UNKNOWN, output.getCurrentState());
    }

    @Test
    void conflictingDriversResolveToUnknown() {
        Wire bus = new Wire("BUS");
        Object firstDriver = new Object();
        Object secondDriver = new Object();

        bus.drive(firstDriver, VoltageState.HIGH);
        bus.drive(secondDriver, VoltageState.LOW);

        assertEquals(VoltageState.UNKNOWN, bus.getCurrentState());
    }
}
