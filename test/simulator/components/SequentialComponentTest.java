package simulator.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.EventEngine;
import simulator.engine.SignalEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SequentialComponentTest {
    private EventEngine engine;

    @BeforeEach
    void setUp() {
        engine = EventEngine.getInstance();
        engine.resetEngine();
        engine.setMaxSimulationTime(1000);
    }

    @Test
    void capturesDataOnRisingClockEdge() {
        Wire data = new Wire("D");
        Wire clock = new Wire("CLK");
        Wire output = new Wire("Q");
        SequentialComponent dff = new SequentialComponent("DFF1", 5, new LowState());

        data.addObserver(dff.getInputPin(0));
        clock.addObserver(dff.getInputPin(1));
        dff.getOutputPin(0).connect(output);

        engine.scheduleEvent(new SignalEvent(0, data, VoltageState.HIGH));
        engine.scheduleEvent(new SignalEvent(10, clock, VoltageState.HIGH));
        engine.runSimulation();

        assertEquals(VoltageState.HIGH, output.getCurrentState());
        assertEquals(15, engine.getCurrentTime());
    }

    @Test
    void capturesUnknownWhenDataIsUnknownOnRisingEdge() {
        Wire clock = new Wire("CLK");
        Wire output = new Wire("Q");
        SequentialComponent dff = new SequentialComponent("DFF1", 0, new LowState());

        clock.addObserver(dff.getInputPin(1));
        dff.getOutputPin(0).connect(output);
        engine.scheduleEvent(new SignalEvent(10, clock, VoltageState.HIGH));
        engine.runSimulation();

        assertEquals(VoltageState.UNKNOWN, output.getCurrentState());
    }
}
