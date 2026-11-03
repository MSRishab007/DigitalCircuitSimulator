package simulator.core;

public class OutputPin {
    private Wire connectedWire;
    
    public void connect(Wire wire) {
        this.connectedWire = wire;
    }
    
    public void driveSignal(VoltageState state) {
        if (connectedWire != null) {
            connectedWire.updateVoltage(state);
        }
    }

    public Wire getConnectedWire() {
        return connectedWire;
    }
}