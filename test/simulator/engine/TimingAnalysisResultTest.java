package simulator.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimingAnalysisResultTest {
    @Test
    void calculatesFrequencyFromCriticalPath() {
        TimingAnalysisResult result = new TimingAnalysisResult(20);

        assertEquals(20, result.getCriticalPathDelay());
        assertEquals(50.0, result.getMaxClockFrequencyMHz());
    }

    @Test
    void rejectsNegativeDelay() {
        assertThrows(IllegalArgumentException.class, () -> new TimingAnalysisResult(-1));
    }
}
