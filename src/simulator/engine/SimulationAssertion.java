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

    public void evaluate(long currentTime) {
        if (!checked && currentTime == time) {
            checked = true;
            if (wire.getCurrentState() != expectedState) {
                throw new IllegalStateException("Assertion failed at t=" + time
                        + ": " + wire.getWireId() + " expected " + expectedState
                        + " but was " + wire.getCurrentState());
            }
        }
    }

    public void verifyCompleted(long currentTime) {
        evaluate(currentTime);
        if (!checked) {
            throw new IllegalStateException("Assertion could not be evaluated at t=" + time
                    + ": simulation ended at t=" + currentTime);
        }
    }
}
