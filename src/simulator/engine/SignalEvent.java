package simulator.engine;

import simulator.core.VoltageState;
import simulator.core.Wire;

public class SignalEvent implements Comparable<SignalEvent> {
    private final long executionTime;
    private final Wire targetWire;
    private final VoltageState newState;

    public SignalEvent(long executionTime, Wire targetWire, VoltageState newState) {
        this.executionTime = executionTime;
        this.targetWire = targetWire;
        this.newState = newState;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public Wire getTargetWire() {
        return targetWire;
    }

    public VoltageState getNewState() {
        return newState;
    }

    @Override
    public int compareTo(SignalEvent other) {
        // This ensures events are processed in chronological order
        return Long.compare(this.executionTime, other.executionTime);
    }
}