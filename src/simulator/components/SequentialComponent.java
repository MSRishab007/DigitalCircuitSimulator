package simulator.components;

import simulator.core.DigitalComponent;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.SignalEvent;

public class SequentialComponent extends DigitalComponent {
    private MemoryState currentState;
    private final int propagationDelay;
    private VoltageState previousClockState = VoltageState.UNKNOWN;

    public SequentialComponent(String id, int delay, MemoryState initialState) {
        // Example: D Flip-Flop has 1 Data Input, 1 Clock Input, 1 Output
        super(id, 2, 1); 
        this.propagationDelay = delay;
        this.currentState = initialState;
    }

    public int getPropagationDelay() {
        return propagationDelay;
    }

    public void clockPulse() {
        VoltageState dataInput = inputPins.get(0).getCurrentState();
        
        // Update internal state variable synchronously[cite: 3]
        MemoryState nextState = currentState.handleClockPulse(dataInput);
        
        if (nextState != currentState) {
            this.currentState = nextState;
            evaluate(); // Trigger asynchronous output generation
        }
    }

    @Override
    public void inputChanged(simulator.core.InputPin pin, VoltageState newState) {
        if (pin == inputPins.get(1)) {
            if (previousClockState != VoltageState.HIGH && newState == VoltageState.HIGH) {
                clockPulse();
            }
            previousClockState = newState;
            return;
        }
        evaluate();
    }

    @Override
    public void evaluate() {
        // Output functions evaluate asynchronously to generate the final electrical signal[cite: 3]
        VoltageState newOutput = currentState.getOutputVoltage();
        Wire targetWire = outputPins.get(0).getConnectedWire();
        
        if (targetWire != null) {
            long executionTime = simulationContext.getEventEngine().getCurrentTime() + propagationDelay;
            SignalEvent event = new SignalEvent(executionTime, targetWire, newOutput, outputPins.get(0));
            simulationContext.getEventEngine().scheduleEvent(event);
        }
    }
}