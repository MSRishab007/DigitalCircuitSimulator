package simulator.parser;

import org.junit.jupiter.api.Test;
import simulator.components.AndGate;
import simulator.core.DigitalComponent;
import simulator.core.Wire;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TopologyValidatorTest {
    @Test
    void rejectsCombinationalFeedbackLoop() {
        Wire a = new Wire("A");
        Wire b = new Wire("B");
        DigitalComponent first = new AndGate("A1", 1);
        DigitalComponent second = new AndGate("A2", 1);

        first.getOutputPin(0).connect(a);
        second.getOutputPin(0).connect(b);
        a.addObserver(first.getInputPin(0));
        a.addObserver(second.getInputPin(0));
        b.addObserver(first.getInputPin(1));
        b.addObserver(second.getInputPin(1));

        assertFalse(new TopologyValidator().validateNoCombinationalLoops(List.of(first, second)));
    }
}
