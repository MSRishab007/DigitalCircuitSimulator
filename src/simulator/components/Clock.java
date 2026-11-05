package simulator.components;

import simulator.core.DigitalComponent;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.SignalEvent;

public class Clock extends DigitalComponent {
    private final int halfPeriod;

    public Clock(String id, int period) {
        super(id, 0, 1);
        if (period < 2 || period % 2 != 0) {
            throw new IllegalArgumentException("Clock period must be an even number of at least 2 ns");
        }
        this.halfPeriod = period / 2;
    }

    @Override
    public void evaluate() {
    }

    public void generateClockPulses() {
        Wire targetWire = outputPins.get(0).getConnectedWire();
        if (targetWire == null) return;

        long maxTime = simulationContext.getEventEngine().getMaxSimulationTime();
        VoltageState state = VoltageState.LOW;

        for (long t = 0; t <= maxTime; t += halfPeriod) {
            simulationContext.getEventEngine().scheduleEvent(new SignalEvent(t, targetWire, state, this));
            state = (state == VoltageState.LOW) ? VoltageState.HIGH : VoltageState.LOW;
        }
    }
}