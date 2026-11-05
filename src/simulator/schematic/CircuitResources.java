package simulator.schematic;

import simulator.core.DigitalComponent;
import simulator.parser.NetlistParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CircuitResources {
    private final int wireCount;
    private final int componentCount;
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    public CircuitResources(NetlistParser parser) {
        wireCount = parser.getAllWires().size();
        componentCount = parser.getComponents().size();
        for (SchematicLayout.Kind kind : SchematicLayout.Kind.values()) {
            int count = 0;
            for (DigitalComponent component : parser.getComponents()) {
                if (SchematicLayout.kindOf(component) == kind) {
                    count++;
                }
            }
            if (count > 0) {
                counts.put(kind.name(), count);
            }
        }
    }

    public int getWireCount() { return wireCount; }
    public int getComponentCount() { return componentCount; }

    public String describe() {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            parts.add(entry.getValue() + " " + entry.getKey());
        }
        return componentCount + " components (" + String.join(", ", parts) + "), " + wireCount + " wires";
    }
}
