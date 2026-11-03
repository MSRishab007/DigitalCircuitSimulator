package simulator.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WireBehaviorTest {
    @Test
    void duplicateObserversAreRegisteredOnce() {
        Wire wire = new Wire("A");
        CountingObserver observer = new CountingObserver();

        wire.addObserver(observer);
        wire.addObserver(observer);
        wire.updateVoltage(VoltageState.HIGH);

        assertEquals(1, observer.updates);
    }

    private static class CountingObserver implements SignalObserver {
        private int updates;

        @Override
        public void update(Wire source, VoltageState newState) {
            updates++;
        }
    }
}
