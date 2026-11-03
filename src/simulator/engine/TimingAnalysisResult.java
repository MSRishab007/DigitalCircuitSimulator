package simulator.engine;

public final class TimingAnalysisResult {
    private final int criticalPathDelay;

    public TimingAnalysisResult(int criticalPathDelay) {
        if (criticalPathDelay < 0) {
            throw new IllegalArgumentException("Critical path delay cannot be negative");
        }
        this.criticalPathDelay = criticalPathDelay;
    }

    public int getCriticalPathDelay() {
        return criticalPathDelay;
    }

    public double getMaxClockFrequencyMHz() {
        return criticalPathDelay == 0 ? 0.0 : 1000.0 / criticalPathDelay;
    }
}
