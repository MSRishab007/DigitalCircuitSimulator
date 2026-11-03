package simulator.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventEngineResetTest {
    @Test
    void resetRestoresDefaultSimulationLimit() {
        EventEngine engine = EventEngine.getInstance();
        engine.setMaxSimulationTime(10);

        engine.resetEngine();

        assertEquals(1000, engine.getMaxSimulationTime());
    }
}
