package simulator.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertEquals(0, new SimulationRunner().runString(netlist + "ASSERT 3 Y X\nASSERT 70 Y 0\n", null)
                .getFailedAssertionCount());

        SimulationRunner.SimulationResult failed = new SimulationRunner().runString(netlist + "ASSERT 70 Y 1\n", null);
        assertEquals(1, failed.getFailedAssertionCount());
        assertEquals("FAIL t=70 Y expected 1, was 0", failed.getAssertions().get(0).describe());
    }

    @Test
    void failsAnAssertionAfterTheEndOfTheSimulation() {
        SimulationRunner.SimulationResult result = new SimulationRunner().runString(
                "SIM_TIME 10\nWIRE A\nSTIMULUS 0 A 1\nASSERT 20 A 1\n", null);

        assertEquals(1, result.getFailedAssertionCount());
        assertTrue(result.getAssertions().get(0).describe().contains("the simulation ended first"));
    }

    @Test
    void reportsEveryAssertionAndKeepsTheWaveform() {
        SimulationRunner.SimulationResult result = new SimulationRunner().runString(
                "SIM_TIME 10\n"
                        + "WIRE A\n"
                        + "STIMULUS 0 A 0\n"
                        + "ASSERT 0 A 1\n"
                        + "ASSERT 1 A 1\n"
                        + "ASSERT 2 A 0\n", null);

        assertEquals(2, result.getFailedAssertionCount());
        assertEquals("PASS t=2 A is 0", result.getAssertions().get(2).describe());
        assertTrue(result.getWaveform().contains("$var wire 1"));
        assertTrue(result.toJson().contains("\"failedAssertions\":2"));
    }
}
