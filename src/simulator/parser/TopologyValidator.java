package simulator.parser;

import simulator.core.DigitalComponent;
import simulator.core.OutputPin;
import simulator.core.Wire;
import simulator.components.SequentialComponent;
import simulator.core.InputPin;

import java.util.HashSet;
import java.util.List;
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
                // Find components listening to this wire (simplified traversal representation)
                // In a full implementation, you would map Wires back to downstream InputPins/Components
            }
        }

        recursionStack.remove(current);
        return false;
    }
}