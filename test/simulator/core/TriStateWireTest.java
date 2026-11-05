package simulator.core;

import org.junit.jupiter.api.Test;

import simulator.components.TriStateBuffer;
import simulator.engine.EventEngine;
import simulator.parser.SimulationRunner;

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

        assertEquals(VoltageState.HIGH_Z, output.getCurrentState());
    }

    @Test
    void aWireStaysUnknownUntilSomethingDrivesIt() {
        assertEquals(VoltageState.UNKNOWN, new Wire("BUS").getCurrentState());
    }

    @Test
    void theBusFollowsTheRemainingDriverAfterARelease() {
        Wire bus = new Wire("BUS");
        Object first = new Object();
        Object second = new Object();

        bus.drive(first, VoltageState.HIGH);
        bus.drive(second, VoltageState.LOW);
        bus.drive(first, VoltageState.HIGH_Z);
        assertEquals(VoltageState.LOW, bus.getCurrentState());

        bus.release(second);
        assertEquals(VoltageState.HIGH_Z, bus.getCurrentState());
    }

    @Test
    void anUnknownDriverMakesTheBusUnknown() {
        Wire bus = new Wire("BUS");
        Object first = new Object();
        Object second = new Object();

        bus.drive(first, VoltageState.HIGH);
        bus.drive(second, VoltageState.UNKNOWN);

        assertEquals(VoltageState.UNKNOWN, bus.getCurrentState());
    }

    @Test
    void aFaultOutranksARelease() {
        Wire bus = new Wire("BUS");
        bus.injectStuckAtFault(VoltageState.LOW);

        bus.drive(new Object(), VoltageState.HIGH_Z);

        assertEquals(VoltageState.LOW, bus.getCurrentState());
    }

    @Test
    void aSharedBusInANetlistReportsZAndGatesReadItAsUnknown() {
        SimulationRunner.SimulationResult result = new SimulationRunner().runString(
                "SIM_TIME 100\nWIRE D\nWIRE E\nWIRE BUS\nWIRE Y\n"
                        + "BUFFER b 5 D E BUS\nNOT n 5 BUS Y\n"
                        + "STIMULUS 0 D 1\nSTIMULUS 0 E 0\nSTIMULUS 40 E 1\nSTIMULUS 70 E 0\n"
                        + "ASSERT 20 BUS Z\nASSERT 20 Y X\n"
                        + "ASSERT 60 BUS 1\nASSERT 60 Y 0\n"
                        + "ASSERT 90 BUS Z\nASSERT 90 Y X\n", null);

        assertEquals(0, result.getFailedAssertionCount());
    }

    @Test
    void aStimulusCanReleaseAWire() {
        SimulationRunner.SimulationResult result = new SimulationRunner().runString(
                "SIM_TIME 50\nWIRE A\nSTIMULUS 0 A 1\nSTIMULUS 20 A Z\nASSERT 10 A 1\nASSERT 30 A Z\n", null);

        assertEquals(0, result.getFailedAssertionCount());
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
