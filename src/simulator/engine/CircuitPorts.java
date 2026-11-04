package simulator.engine;

import simulator.core.DigitalComponent;
import simulator.core.InputPin;
import simulator.core.OutputPin;
import simulator.core.SignalObserver;
import simulator.core.Wire;
import simulator.parser.NetlistParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The externally visible wires of a parsed netlist, in declaration order.
 * Inputs feed a component but have no driver; outputs have a driver but feed nothing.
 */
public final class CircuitPorts {
    private final List<String> wires = new ArrayList<>();
    private final List<String> inputs = new ArrayList<>();
    private final List<String> outputs = new ArrayList<>();
    private final Map<String, String> drivers = new HashMap<>();

    public CircuitPorts(NetlistParser parser) {
        for (DigitalComponent component : parser.getComponents()) {
            for (OutputPin pin : component.getOutputPins()) {
                if (pin.getConnectedWire() != null) {
                    drivers.put(pin.getConnectedWire().getWireId(), component.getComponentId());
                }
            }
        }
        for (Wire wire : parser.getAllWires()) {
            String name = wire.getWireId();
            wires.add(name);
            boolean feedsComponent = false;
            for (SignalObserver observer : wire.getObservers()) {
                feedsComponent |= observer instanceof InputPin;
            }
            if (feedsComponent && !drivers.containsKey(name)) {
                inputs.add(name);
            } else if (!feedsComponent && drivers.containsKey(name)) {
                outputs.add(name);
            }
        }
    }

    public List<String> getWires() { return Collections.unmodifiableList(wires); }
    public List<String> getInputs() { return Collections.unmodifiableList(inputs); }
    public List<String> getOutputs() { return Collections.unmodifiableList(outputs); }

    /** The id of the component driving a wire, or null when the wire has no driver. */
    public String getDriver(String wire) { return drivers.get(wire); }
}
