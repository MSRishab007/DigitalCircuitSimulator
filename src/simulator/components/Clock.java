package simulator.components;

import simulator.core.DigitalComponent;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.EventEngine;
import simulator.engine.SignalEvent;

public class Clock extends DigitalComponent {
    private final int halfPeriod;

    public Clock(String id, int period) {
        super(id, 0, 1); // 0 inputs, 1 output
        this.halfPeriod = period / 2;
    }

    @Override
    public void evaluate() {
        // Clocks are self-driving; they ignore standard input-triggered evaluations.
    }

    // Called once by the simulation facade before execution begins
    public void generateClockPulses() {
        Wire targetWire = outputPins.get(0).getConnectedWire();
        if (targetWire == null) return;

        long maxTime = EventEngine.getInstance().getMaxSimulationTime();
        VoltageState state = VoltageState.LOW;

        // Floods the priority queue with timestamped clock edges
        for (long t = 0; t <= maxTime; t += halfPeriod) {
            EventEngine.getInstance().scheduleEvent(new SignalEvent(t, targetWire, state));
            state = (state == VoltageState.LOW) ? VoltageState.HIGH : VoltageState.LOW;
        }
    }
}