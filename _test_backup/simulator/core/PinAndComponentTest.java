package simulator.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PinAndComponentTest {
    @Test
    void inputPinStoresSignalAndNotifiesParent() {
        RecordingComponent component = new RecordingComponent();
        Wire wire = new Wire("IN");
        InputPin input = component.getInputPin(0);
        wire.addObserver(input);

        wire.updateVoltage(VoltageState.HIGH);

        assertEquals(VoltageState.HIGH, input.getCurrentState());
        assertEquals(1, component.inputChanges);
        assertSame(component, input.getParentComponent());
    }

    @Test
    void outputPinDrivesConnectedWire() {
        RecordingComponent component = new RecordingComponent();
        Wire wire = new Wire("OUT");
        OutputPin output = component.getOutputPin(0);
        output.connect(wire);

        output.driveSignal(VoltageState.HIGH);

        assertSame(wire, output.getConnectedWire());
        assertEquals(VoltageState.HIGH, wire.getCurrentState());
    }

    @Test
    void invalidPinIndexesAreRejected() {
        RecordingComponent component = new RecordingComponent();
        assertThrows(IndexOutOfBoundsException.class, () -> component.getInputPin(1));
        assertThrows(IndexOutOfBoundsException.class, () -> component.getOutputPin(1));
    }

    private static class RecordingComponent extends DigitalComponent {
        private int inputChanges;

        private RecordingComponent() {
            super("REC", 1, 1);
        }

        @Override
        public void inputChanged(InputPin pin, VoltageState newState) {
            inputChanges++;
        }

        @Override
        public void evaluate() {
        }
    }
}
