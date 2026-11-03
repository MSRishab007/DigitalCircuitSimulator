package simulator.core;

import simulator.engine.SimulationContext;
import simulator.engine.SignalEvent;

public class OutputPin {
    private Wire connectedWire;
    private SimulationContext simulationContext = SimulationContext.defaultContext();

    public void setSimulationContext(SimulationContext simulationContext) {
        if (simulationContext == null) {
            throw new IllegalArgumentException("Simulation context cannot be null");
        }
        this.simulationContext = simulationContext;
    }
    
    public void connect(Wire wire) {
        if (wire == null) {
            throw new IllegalArgumentException("Output pin wire cannot be null");
        }
        if (connectedWire == wire) {
            return;
        }
        if (connectedWire != null) {
            connectedWire.release(this);
        }
        this.connectedWire = wire;
    }
    
    public void driveSignal(VoltageState state) {
        if (connectedWire != null) {
            connectedWire.drive(this, state);
        }
    }

    public void scheduleSignal(long executionTime, VoltageState state) {
        if (connectedWire != null) {
            simulationContext.getEventEngine().scheduleEvent(
                    new SignalEvent(executionTime, connectedWire, state, this));
        }
    }

    public Wire getConnectedWire() {
        return connectedWire;
    }
}