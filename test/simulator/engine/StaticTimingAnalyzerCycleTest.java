package simulator.engine;

import org.junit.jupiter.api.Test;
import simulator.components.NotGate;
import simulator.core.Wire;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StaticTimingAnalyzerCycleTest {
    @Test
    void rejectsCombinationalCycles() {
        NotGate first = new NotGate("N1", 1);
        NotGate second = new NotGate("N2", 1);
        Wire firstOutput = new Wire("A");
        Wire secondOutput = new Wire("B");
        first.getOutputPin(0).connect(firstOutput);
        second.getOutputPin(0).connect(secondOutput);
        firstOutput.addObserver(second.getInputPin(0));
        secondOutput.addObserver(first.getInputPin(0));

        assertThrows(IllegalStateException.class,
                () -> new StaticTimingAnalyzer().analyze(List.of(first, second)));
    }
}
