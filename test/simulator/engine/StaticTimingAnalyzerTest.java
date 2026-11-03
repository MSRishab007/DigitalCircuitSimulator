package simulator.engine;

import org.junit.jupiter.api.Test;
import simulator.components.AndGate;
import simulator.components.NotGate;
import simulator.core.Wire;

import java.util.List;

class StaticTimingAnalyzerTest {
    @Test
    void analyzesAcyclicGateChainWithoutFailure() {
        AndGate and = new AndGate("AND", 10);
        NotGate not = new NotGate("NOT", 5);
        Wire intermediate = new Wire("MID");
        Wire output = new Wire("OUT");
        and.getOutputPin(0).connect(intermediate);
        not.getOutputPin(0).connect(output);
        intermediate.addObserver(not.getInputPin(0));

        new StaticTimingAnalyzer().analyze(List.of(and, not));
    }
}
