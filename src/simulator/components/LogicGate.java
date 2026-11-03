package simulator.components;

import simulator.core.DigitalComponent;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.SignalEvent;

public abstract class LogicGate extends DigitalComponent {
    protected int propagationDelay;
    protected VoltageState lastCalculatedState;

    public LogicGate(String id, int numInputs, int numOutputs, int delay) {
        super(id, numInputs, numOutputs);
        this.propagationDelay = delay;
        this.lastCalculatedState = VoltageState.UNKNOWN;
    }

    // Forces concrete gates (AND, OR, NOT) to implement their specific math
    protected abstract VoltageState calculateBooleanLogic();

    @Override
    public void evaluate() {
        // 1. Apply Boolean Mathematics[cite: 10]
        VoltageState newState = calculateBooleanLogic();

        // 2. Did Output Voltage Change?[cite: 10]
        if (newState != lastCalculatedState) {
            lastCalculatedState = newState;
            
            Wire targetWire = outputPins.get(0).getConnectedWire();
            
            if (targetWire != null) {
                // 3. Schedule Event with Propagation Delay[cite: 10]
                long currentTime = simulationContext.getEventEngine().getCurrentTime();
                long executionTime = currentTime + propagationDelay;
                
                SignalEvent event = new SignalEvent(executionTime, targetWire, newState, outputPins.get(0));
                simulationContext.getEventEngine().scheduleEvent(event);
            }
        }
        // 4. Drop Event / Do Nothing if voltage did not change
    }
    public int getPropagationDelay() {
        return this.propagationDelay;
    }
    
}