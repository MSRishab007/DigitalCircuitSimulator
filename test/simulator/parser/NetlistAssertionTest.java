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
