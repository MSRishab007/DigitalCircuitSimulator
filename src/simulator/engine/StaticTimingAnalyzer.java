package simulator.engine;

import simulator.components.LogicGate;
import simulator.components.SequentialComponent;
import simulator.core.DigitalComponent;
import simulator.core.InputPin;
import simulator.core.OutputPin;
import simulator.core.SignalObserver;
import simulator.core.Wire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StaticTimingAnalyzer {
    private final Map<DigitalComponent, Integer> pathDelays = new HashMap<>();
    private final Map<DigitalComponent, DigitalComponent> nextOnPath = new HashMap<>();
    private int criticalPathDelay;

    public TimingAnalysisResult analyze(List<DigitalComponent> components) {
        if (components == null) {
            throw new IllegalArgumentException("Components cannot be null");
        }
        pathDelays.clear();
        nextOnPath.clear();
        criticalPathDelay = 0;
        DigitalComponent start = null;
        for (DigitalComponent component : components) {
            int delay = launchDelay(component);
            if (start == null || delay > criticalPathDelay) {
                criticalPathDelay = delay;
                start = component;
            }
        }
        return new TimingAnalysisResult(criticalPathDelay, tracePath(start));
    }

    public int getCriticalPathDelay() {
        return criticalPathDelay;
    }

    private List<String> tracePath(DigitalComponent start) {
        List<String> path = new ArrayList<>();
        if (criticalPathDelay == 0) {
            return path;
        }
        DigitalComponent current = start;
        while (current != null) {
            if (ownDelay(current) > 0 || !path.isEmpty()) {
                path.add(current.getComponentId());
            }
            current = nextOnPath.get(current);
        }
        return path;
    }

    private static int ownDelay(DigitalComponent component) {
        if (component instanceof LogicGate) {
            return ((LogicGate) component).getPropagationDelay();
        }
        if (component instanceof SequentialComponent) {
            return ((SequentialComponent) component).getPropagationDelay();
        }
        return 0;
    }

    private int launchDelay(DigitalComponent component) {
        Integer cached = pathDelays.get(component);
        if (cached != null) {
            return cached;
        }
        return component instanceof SequentialComponent
                ? measure(component, new HashSet<>()) : pathFrom(component, new HashSet<>());
    }

    private int pathFrom(DigitalComponent current, Set<DigitalComponent> visiting) {
        if (current instanceof SequentialComponent) {
            return 0;
        }
        Integer cached = pathDelays.get(current);
        if (cached != null) {
            return cached;
        }
        return measure(current, visiting);
    }

    private int measure(DigitalComponent current, Set<DigitalComponent> visiting) {
        if (!visiting.add(current)) {
            throw new IllegalStateException("Combinational cycle detected during timing analysis at "
                    + current.getComponentId());
        }
        int longest = 0;
        DigitalComponent next = null;
        for (OutputPin pin : current.getOutputPins()) {
            Wire wire = pin.getConnectedWire();
            if (wire == null) {
                continue;
            }
            for (SignalObserver observer : wire.getObservers()) {
                if (observer instanceof InputPin) {
                    InputPin input = (InputPin) observer;
                    DigitalComponent target = input.getParentComponent();
                    boolean clockPin = target instanceof SequentialComponent && target.getInputPin(1) == input;
                    int delay = clockPin ? 0 : pathFrom(target, visiting);
                    if (!clockPin && (next == null || delay > longest)) {
                        longest = delay;
                        next = target;
                    }
                }
            }
        }
        visiting.remove(current);
        int total = ownDelay(current) + longest;
        pathDelays.put(current, total);
        if (next != null && !(next instanceof SequentialComponent)) {
            nextOnPath.put(current, next);
        }
        return total;
    }
}
