package simulator.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class Wire {
    private final String wireId;
    private VoltageState currentState;
    private final List<SignalObserver> observers;
    private VoltageState stuckAtState;
    private final Map<Object, VoltageState> driverStates;
    private static final Object EXTERNAL_DRIVER = new Object();

    public Wire(String wireId) {
        this.wireId = wireId;
        this.currentState = VoltageState.UNKNOWN;
        this.observers = new ArrayList<>();
        this.stuckAtState = null;
        this.driverStates = new IdentityHashMap<>();
    }

    public void addObserver(SignalObserver observer) {
        observers.add(observer);
    }

    public void injectStuckAtFault(VoltageState faultState) {
        if (faultState != VoltageState.HIGH && faultState != VoltageState.LOW) {
            throw new IllegalArgumentException("Stuck-at faults must be HIGH or LOW");
        }
        stuckAtState = faultState;
        driverStates.clear();
        currentState = faultState;
        notifyObservers();
    }

    public void updateVoltage(VoltageState newState) {
        if (stuckAtState == null && (newState == VoltageState.HIGH_Z || driverStates.isEmpty())) {
            updateLegacyVoltage(newState);
            return;
        }
        drive(EXTERNAL_DRIVER, newState);
    }

    public void drive(Object driver, VoltageState newState) {
        if (driver == null) {
            throw new IllegalArgumentException("Wire driver cannot be null");
        }
        if (stuckAtState != null) {
            return;
        }
        if (newState == VoltageState.HIGH_Z) {
            driverStates.remove(driver);
        } else {
            driverStates.put(driver, newState);
        }
        VoltageState resolvedState = resolveDrivers();
        if (resolvedState != currentState) {
            currentState = resolvedState;
            notifyObservers();
        }
    }

    private void updateLegacyVoltage(VoltageState newState) {
        if (newState == VoltageState.HIGH_Z) {
            return;
        }
        currentState = newState;
        notifyObservers();
    }

    private VoltageState resolveDrivers() {
        boolean high = false;
        boolean low = false;
        for (VoltageState state : driverStates.values()) {
            high |= state == VoltageState.HIGH;
            low |= state == VoltageState.LOW;
        }
        if (high && low) {
            System.err.println("WARNING: Bus Contention (Short Circuit) on wire " + wireId);
            return VoltageState.UNKNOWN;
        }
        if (high) return VoltageState.HIGH;
        if (low) return VoltageState.LOW;
        return VoltageState.UNKNOWN;
    }

    private void notifyObservers() {
        for (SignalObserver observer : observers) {
            observer.update(this, currentState);
        }
    }

    public String getWireId() { return wireId; }
    public VoltageState getCurrentState() { return currentState; }
    public List<SignalObserver> getObservers() { return observers; }
}
