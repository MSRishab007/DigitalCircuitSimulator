package simulator.components;

import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.EventEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClockTest {
    @Test
    void rejectsPeriodsThatCannotProduceAValidHalfPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new Clock("CLK", 1));
        assertThrows(IllegalArgumentException.class, () -> new Clock("CLK", 0));
        assertThrows(IllegalArgumentException.class, () -> new Clock("CLK", -2));
    }

    @Test
    void generatesAlternatingEdgesWithinSimulationWindow() {
        EventEngine engine = EventEngine.getInstance();
        engine.resetEngine();
        engine.setMaxSimulationTime(10);
        Wire output = new Wire("CLK");
        Clock clock = new Clock("CLK1", 4);
        clock.getOutputPin(0).connect(output);

        clock.generateClockPulses();
        engine.runSimulation();

        assertEquals(VoltageState.HIGH, output.getCurrentState());
        assertEquals(10, engine.getCurrentTime());
        assertEquals(6, engine.getTotalEventsProcessed());
    }
}
