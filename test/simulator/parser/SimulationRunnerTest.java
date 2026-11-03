package simulator.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationRunnerTest {
    @Test
    void runsTheSameSimulationPipelineWithoutWaveformOutput() {
        SimulationRunner.SimulationResult result = new SimulationRunner().runString(
                "SIM_TIME 10\nWIRE A\nSTIMULUS 0 A 1\n", null);

        assertEquals(1, result.getWireCount());
        assertEquals(0, result.getComponentCount());
        assertEquals(1, result.getEventsProcessed());
        assertEquals(0, result.getCriticalPathDelay());
        assertEquals(true, result.toJson().contains("\"success\":true"));
        assertEquals(true, result.getWaveform().contains("$enddefinitions $end"));
    }
}
