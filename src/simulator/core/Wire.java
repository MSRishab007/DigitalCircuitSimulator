package simulator.core;

import java.util.ArrayList;
import java.util.List;

public class Wire {
    private final String wireId;
    private VoltageState currentState;
    private final List<SignalObserver> observers;
    private VoltageState stuckAtState; // Tracks hardware defects

    public Wire(String wireId) {
        this.wireId = wireId;
        this.currentState = VoltageState.UNKNOWN;
        this.observers = new ArrayList<>();
        this.stuckAtState = null;
    }

    public void addObserver(SignalObserver observer) {
        this.observers.add(observer);
    }

    public void injectStuckAtFault(VoltageState faultState) {
        this.stuckAtState = faultState;
        this.currentState = faultState;
        notifyObservers();
    }

    public void updateVoltage(VoltageState newState) {
        // 1. Fault check: If hardware is physically shorted, ignore logic updates
        if (this.stuckAtState != null) {
            return;
        }

        // 2. High Impedance (disconnect) does not override active signals
        if (newState == VoltageState.HIGH_Z && this.currentState != VoltageState.UNKNOWN) {
            return; 
        }
        
        // 3. Bus Contention: If wire is driven HIGH and LOW simultaneously
        if ((this.currentState == VoltageState.HIGH && newState == VoltageState.LOW) ||
            (this.currentState == VoltageState.LOW && newState == VoltageState.HIGH)) {
            this.currentState = VoltageState.UNKNOWN;
            System.err.println("WARNING: Bus Contention (Short Circuit) on wire " + wireId);
        } else {
            this.currentState = newState;
        }
        notifyObservers();
    }

    private void notifyObservers() {
        for (SignalObserver observer : observers) {
            observer.update(this, this.currentState);
        }
    }

    public String getWireId() { return wireId; }
    public VoltageState getCurrentState() { return currentState; }
    public List<SignalObserver> getObservers() { return observers; }
}