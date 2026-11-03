package simulator.components;

import simulator.core.DigitalComponent;
import java.util.ArrayList;
import java.util.List;

public class Module extends DigitalComponent {
    private final List<DigitalComponent> internalComponents;

    public Module(String id, int numInputs, int numOutputs) {
        super(id, numInputs, numOutputs);
        this.internalComponents = new ArrayList<>();
    }

    public void addSubComponent(DigitalComponent c) {
        internalComponents.add(c);
    }

    @Override
    public void evaluate() {
        // The simulation engine simply calls the evaluation method on any given component
        // The Composite pattern cascades this evaluation to all internal structural children
        for (DigitalComponent component : internalComponents) {
            component.evaluate();
        }
    }
}