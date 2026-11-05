package simulator.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaultCoverageAnalyzerTest {
    private static final String GATE = "SIM_TIME 100\nWIRE A\nWIRE B\nWIRE Y\nAND g 5 A B Y\n";

    @Test
    void exhaustiveStimuliDetectEveryFault() {
        FaultCoverageAnalyzer.Result result = new FaultCoverageAnalyzer().analyze(GATE
                + "STIMULUS 0 A 0\nSTIMULUS 0 B 1\nSTIMULUS 20 A 1\nSTIMULUS 40 B 0\nSTIMULUS 60 A 0\n");

        assertEquals(6, result.getTotalFaults());
        assertEquals(List.of(), result.getUndetected());
        assertEquals(100.0, result.getCoveragePercent());
    }

    @Test
    void listsFaultsTheStimuliCannotReveal() {
        FaultCoverageAnalyzer.Result result = new FaultCoverageAnalyzer().analyze(GATE
                + "STIMULUS 0 A 0\nSTIMULUS 0 B 0\n");

        assertTrue(result.getUndetected().contains("Y stuck at 0"));
        assertTrue(result.getUndetected().contains("A stuck at 0"));
        assertTrue(!result.getUndetected().contains("Y stuck at 1"));
        assertTrue(result.getCoveragePercent() < 100);
    }

    @Test
    void skipsAWireThatAlreadyHasAFault() {
        FaultCoverageAnalyzer.Result result = new FaultCoverageAnalyzer().analyze(GATE
                + "STIMULUS 0 A 1\nSTIMULUS 0 B 1\nFAULT STUCK_AT_1 B\n");

        assertEquals(4, result.getTotalFaults());
    }

    @Test
    void observesFlipFlopsInASequentialCircuit() {
        FaultCoverageAnalyzer.Result result = new FaultCoverageAnalyzer().analyze(
                "SIM_TIME 100\nWIRE CLK\nWIRE Q\nWIRE N\nCLOCK c 20 CLK\nNOT n 1 Q N\nDFF f 1 N CLK Q\n");

        assertTrue(result.getObservedWires().contains("Q"));
        assertTrue(result.getDetectedFaults() > 0);
    }

    @Test
    void needsAnOutputToObserve() {
        assertThrows(IllegalArgumentException.class, () -> new FaultCoverageAnalyzer().analyze("WIRE A\nSTIMULUS 0 A 1\n"));
    }
}
