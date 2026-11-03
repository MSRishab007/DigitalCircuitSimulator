package simulator.components;

import org.junit.jupiter.api.Test;
import simulator.core.DigitalComponent;
import simulator.core.InputPin;
import simulator.core.VoltageState;
import simulator.core.Wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleBindingTest {
    @Test
    void forwardsInputAndOutputSignalsThroughBindings() {
        Module module = new Module("M", 1, 1);
        RecordingComponent child = new RecordingComponent();
        Wire childOutput = new Wire("CHILD_OUT");
        Wire moduleOutput = new Wire("MODULE_OUT");

        module.addSubComponent(child);
        module.bindInput(0, child.getInputPin(0));
        module.bindOutput(0, childOutput);
        module.getOutputPin(0).connect(moduleOutput);

        Wire moduleInput = new Wire("MODULE_IN");
        moduleInput.addObserver(module.getInputPin(0));
        childOutput.updateVoltage(VoltageState.HIGH);
        moduleInput.updateVoltage(VoltageState.LOW);

        assertEquals(VoltageState.LOW, child.lastInput);
        assertEquals(VoltageState.HIGH, moduleOutput.getCurrentState());
    }

    private static class RecordingComponent extends DigitalComponent {
        private VoltageState lastInput = VoltageState.UNKNOWN;

        private RecordingComponent() {
            super("CHILD", 1, 1);
        }

        @Override
        public void inputChanged(InputPin pin, VoltageState newState) {
            lastInput = newState;
        }

        @Override
        public void evaluate() {
        }

        @Test
        void rejectsInvalidAndDuplicateBindings() {
            Module module = new Module("M", 1, 1);
            RecordingComponent child = new RecordingComponent();
            Wire output = new Wire("OUT");
            module.addSubComponent(child);

            assertThrows(IndexOutOfBoundsException.class,
                    () -> module.bindInput(1, child.getInputPin(0)));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> module.bindOutput(1, output));
            module.bindInput(0, child.getInputPin(0));
            assertThrows(IllegalArgumentException.class,
                    () -> module.bindInput(0, child.getInputPin(0)));
            module.bindOutput(0, output);
            assertThrows(IllegalArgumentException.class,
                    () -> module.bindOutput(0, output));
        }
    }
}
