package simulator.engine;

import java.util.Collections;
import java.util.List;

public final class TimingAnalysisResult {
    private final int criticalPathDelay;
    private final List<String> criticalPath;

    public TimingAnalysisResult(int criticalPathDelay) {
        this(criticalPathDelay, List.of());
    }

    public TimingAnalysisResult(int criticalPathDelay, List<String> criticalPath) {
        if (criticalPathDelay < 0) {
            throw new IllegalArgumentException("Critical path delay cannot be negative");
        }
        this.criticalPathDelay = criticalPathDelay;
        this.criticalPath = Collections.unmodifiableList(criticalPath);
    }

    public int getCriticalPathDelay() {
        return criticalPathDelay;
    }

    public List<String> getCriticalPath() {
        return criticalPath;
    }

    public double getMaxClockFrequencyMHz() {
        return criticalPathDelay == 0 ? 0.0 : 1000.0 / criticalPathDelay;
    }
}
