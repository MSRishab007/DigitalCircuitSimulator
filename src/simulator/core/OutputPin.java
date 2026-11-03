package simulator.core;

import simulator.engine.EventEngine;
import simulator.engine.SignalEvent;

public class OutputPin {
    private Wire connectedWire;
    
    public void connect(Wire wire) {
        this.connectedWire = wire;
    }
    
    public void driveSignal(VoltageState state) {
        if (connectedWire != null) {
            connectedWire.drive(this, state);
        }
    }

    public void scheduleSignal(long executionTime, VoltageState state) {
        if (connectedWire != null) {
            EventEngine.getInstance().scheduleEvent(
                    new SignalEvent(executionTime, connectedWire, state, this));
        }
    }

    public Wire getConnectedWire() {
        return connectedWire;
    }
}