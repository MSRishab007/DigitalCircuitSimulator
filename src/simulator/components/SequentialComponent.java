package simulator.components;

import simulator.core.DigitalComponent;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.EventEngine;
import simulator.engine.SignalEvent;

public class SequentialComponent extends DigitalComponent {
    private MemoryState currentState;
    private final int propagationDelay;

    public SequentialComponent(String id, int delay, MemoryState initialState) {
        // Example: D Flip-Flop has 1 Data Input, 1 Clock Input, 1 Output
        super(id, 2, 1); 
        this.propagationDelay = delay;
        this.currentState = initialState;
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
    public void evaluate() {
        // Output functions evaluate asynchronously to generate the final electrical signal[cite: 3]
        VoltageState newOutput = currentState.getOutputVoltage();
        Wire targetWire = outputPins.get(0).getConnectedWire();
        
        if (targetWire != null) {
            long executionTime = EventEngine.getInstance().getCurrentTime() + propagationDelay;
            SignalEvent event = new SignalEvent(executionTime, targetWire, newOutput);
            EventEngine.getInstance().scheduleEvent(event);
        }
    }
}