package simulator.engine;

import simulator.core.VoltageState;
import simulator.core.Wire;

public class SignalEvent implements Comparable<SignalEvent> {
    private static long nextSequence;
    private final long executionTime;
    private final long sequence;
    private final Wire targetWire;
    private final VoltageState newState;
    private final Object driver;

    public SignalEvent(long executionTime, Wire targetWire, VoltageState newState) {
        this(executionTime, targetWire, newState, SignalEvent.class);
    }

    public SignalEvent(long executionTime, Wire targetWire, VoltageState newState, Object driver) {
        this.executionTime = executionTime;
        this.sequence = nextSequence++;
        this.targetWire = targetWire;
        this.newState = newState;
        this.driver = driver;
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

    public Object getDriver() {
        return driver;
    }

    public static void resetSequence() {
        nextSequence = 0;
    }

    @Override
    public int compareTo(SignalEvent other) {
        int timeComparison = Long.compare(this.executionTime, other.executionTime);
        return timeComparison != 0 ? timeComparison : Long.compare(this.sequence, other.sequence);
    }
}