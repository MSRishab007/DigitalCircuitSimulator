package simulator.components;

import org.junit.jupiter.api.Test;
import simulator.core.DigitalComponent;
import simulator.core.VoltageState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StateAndModuleTest {
    @Test
    void lowAndHighStatesTransitionOnData() {
        LowState low = new LowState();
        HighState high = new HighState();

        assertEquals(VoltageState.LOW, low.getOutputVoltage());
        assertEquals(VoltageState.HIGH, high.getOutputVoltage());
        assertTrueState(low.handleClockPulse(VoltageState.HIGH), HighState.class);
        assertTrueState(high.handleClockPulse(VoltageState.LOW), LowState.class);
        assertSame(low, low.handleClockPulse(VoltageState.LOW));
        assertSame(high, high.handleClockPulse(VoltageState.HIGH));
    }

    @Test
    void moduleEvaluatesAllChildren() {
        CountingComponent first = new CountingComponent();
        CountingComponent second = new CountingComponent();
        Module module = new Module("M", 0, 0);
        module.addSubComponent(first);
        module.addSubComponent(second);

        module.evaluate();

        assertEquals(1, first.evaluations);
        assertEquals(1, second.evaluations);
    }

    private void assertTrueState(MemoryState state, Class<?> expectedType) {
        org.junit.jupiter.api.Assertions.assertInstanceOf(expectedType, state);
    }

    private static class CountingComponent extends DigitalComponent {
        private int evaluations;

        private CountingComponent() {
            super("COUNT", 0, 0);
        }

        @Override
        public void evaluate() {
            evaluations++;
        }
    }
}
