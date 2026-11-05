package simulator.engine;

import simulator.core.VoltageState;
import simulator.core.Wire;

public final class SimulationAssertion {
    private final long time;
    private final Wire wire;
    private final VoltageState expectedState;
    private VoltageState actualState;

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
        if (actualState == null) {
            actualState = wire.getCurrentState();
        }
    }

    public boolean wasChecked() {
        return actualState != null;
    }

    public boolean hasPassed() {
        return actualState == expectedState;
    }

    public String describe() {
        String where = "t=" + time + " " + wire.getWireId();
        if (actualState == null) {
            return "FAIL " + where + " expected " + symbol(expectedState) + ", but the simulation ended first";
        }
        return hasPassed() ? "PASS " + where + " is " + symbol(actualState)
                : "FAIL " + where + " expected " + symbol(expectedState) + ", was " + symbol(actualState);
    }

    private static String symbol(VoltageState state) {
        switch (state) {
            case HIGH:
                return "1";
            case LOW:
                return "0";
            case HIGH_Z:
                return "Z";
            default:
                return "X";
        }
    }
}
