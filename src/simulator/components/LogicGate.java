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

    protected abstract VoltageState calculateBooleanLogic();

    @Override
    public void evaluate() {
        VoltageState newState = calculateBooleanLogic();

        if (newState != lastCalculatedState) {
            lastCalculatedState = newState;
            
            Wire targetWire = outputPins.get(0).getConnectedWire();
            
            if (targetWire != null) {
                long currentTime = simulationContext.getEventEngine().getCurrentTime();
                long executionTime = currentTime + propagationDelay;
                
                SignalEvent event = new SignalEvent(executionTime, targetWire, newState, outputPins.get(0));
                simulationContext.getEventEngine().scheduleEvent(event);
            }
        }
    }
    public int getPropagationDelay() {
        return this.propagationDelay;
    }
    
}