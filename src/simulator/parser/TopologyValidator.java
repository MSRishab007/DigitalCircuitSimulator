package simulator.parser;

import simulator.core.DigitalComponent;
import simulator.core.OutputPin;
import simulator.core.Wire;
import simulator.components.SequentialComponent;
import simulator.core.InputPin;
import simulator.core.SignalObserver;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TopologyValidator {
    
    public boolean validateNoCombinationalLoops(List<DigitalComponent> components) {
        Set<DigitalComponent> visited = new HashSet<>();
        Set<DigitalComponent> recursionStack = new HashSet<>();

        for (DigitalComponent component : components) {
            if (hasCycle(component, visited, recursionStack)) {
                return false; // Cycle detected
            }
        }
        return true; // Validation passed
    }

    private boolean hasCycle(DigitalComponent current, Set<DigitalComponent> visited, Set<DigitalComponent> recursionStack) {
        // Sequential components (like Flip-Flops) act as valid cycle breakers.
        // We do not traverse through them for unclocked loop detection.
        if (current instanceof SequentialComponent) {
            return false;
        }

        if (recursionStack.contains(current)) {
            return true; // Unclocked loop found
        }
        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        recursionStack.add(current);

        // Traverse to downstream components connected via wires
        for (OutputPin outputPin : current.getOutputPins()) { // Direct access per DigitalComponent spec
            Wire wire = outputPin.getConnectedWire();
            if (wire != null) {
                for (SignalObserver observer : wire.getObservers()) {
                    if (observer instanceof InputPin &&
                            hasCycle(((InputPin) observer).getParentComponent(), visited, recursionStack)) {
                        return true;
                    }
                }
            }
        }

        recursionStack.remove(current);
        return false;
    }

    /** Returns a description of a wire that gates other than tri-state buffers share, or null when there is none. */
    public String findDriverConflict(List<DigitalComponent> components) {
        Map<simulator.core.Wire, List<DigitalComponent>> drivers = new java.util.LinkedHashMap<>();
        for (DigitalComponent component : components) {
            for (simulator.core.OutputPin pin : component.getOutputPins()) {
                if (pin.getConnectedWire() != null) {
                    drivers.computeIfAbsent(pin.getConnectedWire(), wire -> new java.util.ArrayList<>()).add(component);
                }
            }
        }
        for (Map.Entry<simulator.core.Wire, List<DigitalComponent>> entry : drivers.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (DigitalComponent driver : entry.getValue()) {
                    if (!(driver instanceof simulator.components.TriStateBuffer)) {
                        return "Wire " + entry.getKey().getWireId() + " is driven by more than one component. "
                                + "Only tri-state buffers can share a wire.";
                    }
                }
            }
        }
        return null;
    }
}
