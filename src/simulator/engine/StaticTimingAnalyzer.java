package simulator.engine;

import simulator.components.LogicGate;
import simulator.components.SequentialComponent;
import simulator.core.DigitalComponent;
import simulator.core.InputPin;
import simulator.core.SignalObserver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StaticTimingAnalyzer {
    private final Map<DigitalComponent, Integer> pathDelays = new HashMap<>();
    private int criticalPathDelay;

    public void analyze(List<DigitalComponent> components) {
        pathDelays.clear();
        criticalPathDelay = 0;
        for (DigitalComponent component : components) {
            criticalPathDelay = Math.max(criticalPathDelay,
                    calculateMaxPath(component, new HashSet<>()));
        }

    }

    public int getCriticalPathDelay() {
        return criticalPathDelay;
    }

    private int calculateMaxPath(DigitalComponent current, Set<DigitalComponent> visiting) {
        if (current instanceof SequentialComponent) {
            return 0;
        }
        Integer cached = pathDelays.get(current);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(current)) {
            throw new IllegalStateException("Combinational cycle detected during timing analysis at "
                    + current.getComponentId());
        }

        int maxDownstreamDelay = 0;
        for (simulator.core.OutputPin outPin : current.getOutputPins()) {
            simulator.core.Wire connectedWire = outPin.getConnectedWire();
            if (connectedWire == null) {
                continue;
            }
            for (SignalObserver observer : connectedWire.getObservers()) {
                if (observer instanceof InputPin) {
                    maxDownstreamDelay = Math.max(maxDownstreamDelay,
                            calculateMaxPath(((InputPin) observer).getParentComponent(), visiting));
                }
            }
        }
        visiting.remove(current);

        int ownDelay = current instanceof LogicGate
                ? ((LogicGate) current).getPropagationDelay() : 0;
        int totalDelay = ownDelay + maxDownstreamDelay;
        pathDelays.put(current, totalDelay);
        return totalDelay;
    }
}
