package simulator.engine;

import simulator.core.VoltageState;
import simulator.core.Wire;

public final class SimulationAssertion {
    private final long time;
    private final Wire wire;
    private final VoltageState expectedState;
    private boolean checked;

    public SimulationAssertion(long time, Wire wire, VoltageState expectedState) {
        if (time < 0) {
            throw new IllegalArgumentException("Assertion time cannot be negative");
        }
        if (wire == null || expectedState == null) {
            throw new IllegalArgumentException("Assertion wire and state are required");
        }
        this.time = time;
        this.wire = wire;
        this.expectedState = expectedState;
    }

    public long getTime() {
        return time;
    }

    public void check() {
        if (!checked) {
            checked = true;
            if (wire.getCurrentState() != expectedState) {
                throw new IllegalStateException("Assertion failed at t=" + time
                        + ": " + wire.getWireId() + " expected " + expectedState
                        + " but was " + wire.getCurrentState());
            }
        }
    }

    public void verifyCompleted(long maxSimulationTime) {
        if (!checked) {
            throw new IllegalStateException("Assertion at t=" + time
                    + " is after the end of the simulation at t=" + maxSimulationTime
                    + ". Increase SIM_TIME or move the assertion.");
        }
    }
}
