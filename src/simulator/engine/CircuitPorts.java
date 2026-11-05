package simulator.engine;

import simulator.components.SequentialComponent;
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
    private final List<String> stateWires = new ArrayList<>();
    private final List<String> nextStateWires = new ArrayList<>();
    private final List<String> clockWires = new ArrayList<>();

    public CircuitPorts(NetlistParser parser) {
        for (DigitalComponent component : parser.getComponents()) {
            for (OutputPin pin : component.getOutputPins()) {
                if (pin.getConnectedWire() != null) {
                    drivers.put(pin.getConnectedWire().getWireId(), component.getComponentId());
                }
            }
        }
        Map<InputPin, String> pinWires = new java.util.IdentityHashMap<>();
        for (Wire wire : parser.getAllWires()) {
            for (SignalObserver observer : wire.getObservers()) {
                if (observer instanceof InputPin) {
                    pinWires.put((InputPin) observer, wire.getWireId());
                }
            }
        }
        for (DigitalComponent component : parser.getComponents()) {
            if (component instanceof SequentialComponent) {
                Wire state = component.getOutputPin(0).getConnectedWire();
                stateWires.add(state.getWireId());
                nextStateWires.add(pinWires.get(component.getInputPin(0)));
                clockWires.add(pinWires.get(component.getInputPin(1)));
            }
        }
        for (Wire wire : parser.getAllWires()) {
            String name = wire.getWireId();
            wires.add(name);
            boolean feedsData = false;
            boolean feedsClock = false;
            for (SignalObserver observer : wire.getObservers()) {
                if (observer instanceof InputPin) {
                    InputPin pin = (InputPin) observer;
                    boolean clockPin = pin.getParentComponent() instanceof SequentialComponent
                            && pin.getParentComponent().getInputPin(1) == pin;
                    feedsClock |= clockPin;
                    feedsData |= !clockPin;
                }
            }
            if (feedsData && !drivers.containsKey(name)) {
                inputs.add(name);
            } else if (!feedsData && !feedsClock && drivers.containsKey(name)) {
                outputs.add(name);
            }
        }
    }

    public List<String> getWires() { return Collections.unmodifiableList(wires); }
    public List<String> getInputs() { return Collections.unmodifiableList(inputs); }
    public List<String> getOutputs() { return Collections.unmodifiableList(outputs); }

    public boolean isSequential() { return !stateWires.isEmpty(); }
    public List<String> getStateWires() { return Collections.unmodifiableList(stateWires); }
    public List<String> getNextStateWires() { return Collections.unmodifiableList(nextStateWires); }
    public List<String> getClockWires() { return Collections.unmodifiableList(clockWires); }

    /** The id of the component driving a wire, or null when the wire has no driver. */
    public String getDriver(String wire) { return drivers.get(wire); }
}
