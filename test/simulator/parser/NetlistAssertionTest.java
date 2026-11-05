package simulator.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetlistAssertionTest {
    @Test
    void evaluatesAssertionsAtSimulationTime() {
        SimulationRunner.SimulationResult result = new SimulationRunner().runString(
                "SIM_TIME 10\n"
                        + "WIRE A\n"
                        + "STIMULUS 0 A 1\n"
                        + "ASSERT 0 A 1\n", null);

        assertTrue(result.getEventsProcessed() > 0);
    }

    @Test
    void checksASettledValueWhenNoEventHappensAtThatTime() {
        String netlist = "SIM_TIME 100\nWIRE A\nWIRE Y\nNOT n 5 A Y\nSTIMULUS 0 A 1\n";

        new SimulationRunner().runString(netlist + "ASSERT 3 Y X\nASSERT 70 Y 0\n", null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new SimulationRunner().runString(netlist + "ASSERT 70 Y 1\n", null));
        assertTrue(failure.getMessage().contains("Assertion failed at t=70"));
    }

    @Test
    void rejectsAnAssertionAfterTheEndOfTheSimulation() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new SimulationRunner().runString(
                        "SIM_TIME 10\nWIRE A\nSTIMULUS 0 A 1\nASSERT 20 A 1\n", null));

        assertTrue(failure.getMessage().contains("Increase SIM_TIME"));
    }

    @Test
    void reportsAssertionFailures() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new SimulationRunner().runString(
                        "SIM_TIME 10\n"
                                + "WIRE A\n"
                                + "STIMULUS 0 A 0\n"
                                + "ASSERT 0 A 1\n", null));

        assertTrue(failure.getMessage().contains("Assertion failed"));
    }
}
