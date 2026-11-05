package simulator.components;

import simulator.core.DigitalComponent;
import simulator.core.InputPin;
import simulator.core.OutputPin;
import simulator.core.SignalObserver;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.SimulationContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class Module extends DigitalComponent {
    private final List<DigitalComponent> internalComponents;
    private final List<InputPin> inputBindings;
    private final Map<InputPin, Boolean> boundInputs;
    private final Map<Integer, Wire> boundOutputs;
    private String typeName = "MODULE";

    public Module(String id, int numInputs, int numOutputs) {
        super(id, numInputs, numOutputs);
        this.internalComponents = new ArrayList<>();
        this.inputBindings = new ArrayList<>();
        this.boundInputs = new IdentityHashMap<>();
        this.boundOutputs = new java.util.HashMap<>();
        for (int i = 0; i < numInputs; i++) {
            inputBindings.add(null);
        }

    }

    @Override
    public void setSimulationContext(SimulationContext simulationContext) {
        super.setSimulationContext(simulationContext);
        for (DigitalComponent component : internalComponents) {
            component.setSimulationContext(simulationContext);
        }
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    public void addSubComponent(DigitalComponent c) {
        if (c == null) {
            throw new IllegalArgumentException("Module child cannot be null");
        }
        if (c == this) {
            throw new IllegalArgumentException("Module cannot contain itself");
        }
        internalComponents.add(c);
        c.setSimulationContext(simulationContext);
    }

    public void bindInput(int moduleInput, InputPin internalInput) {
        validateInputIndex(moduleInput);
        if (internalInput == null) {
            throw new IllegalArgumentException("Module input binding cannot be null");
        }
        InputPin previous = inputBindings.get(moduleInput);
        if (previous != null) {
            boundInputs.remove(previous);
        }
        if (boundInputs.put(internalInput, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Internal input is already bound");
        }
        inputBindings.set(moduleInput, internalInput);
    }

    public void bindOutput(int moduleOutput, Wire internalOutput) {
        validateOutputIndex(moduleOutput);
        if (internalOutput == null) {
            throw new IllegalArgumentException("Module output binding cannot be null");
        }
        if (boundOutputs.containsKey(moduleOutput)) {
            throw new IllegalArgumentException("Module output is already bound");
        }
        boundOutputs.put(moduleOutput, internalOutput);
        OutputBridge bridge = new OutputBridge(getOutputPin(moduleOutput));
        internalOutput.addObserver(bridge);
    }

    public List<DigitalComponent> getSubComponents() {
        return Collections.unmodifiableList(internalComponents);
    }

    @Override
    public void inputChanged(InputPin pin, VoltageState newState) {
        int index = inputPins.indexOf(pin);
        if (index < 0) {
            throw new IllegalArgumentException("Input pin does not belong to module");
        }
        InputPin internalInput = inputBindings.get(index);
        if (internalInput != null) {
            internalInput.update(null, newState);
        }
    }

    @Override
    public void evaluate() {
        // The simulation engine simply calls the evaluation method on any given component
        // The Composite pattern cascades this evaluation to all internal structural children
        for (DigitalComponent component : internalComponents) {
            component.evaluate();
        }
    }

    private void validateInputIndex(int index) {
        if (index < 0 || index >= inputPins.size()) {
            throw new IndexOutOfBoundsException("Invalid module input index: " + index);
        }
    }

    private void validateOutputIndex(int index) {
        if (index < 0 || index >= outputPins.size()) {
            throw new IndexOutOfBoundsException("Invalid module output index: " + index);
        }
    }

    private static final class OutputBridge implements SignalObserver {
        private final OutputPin moduleOutput;

        private OutputBridge(OutputPin moduleOutput) {
            this.moduleOutput = moduleOutput;
        }

        @Override
        public void update(Wire source, VoltageState newState) {
            moduleOutput.driveSignal(newState);
        }
    }
}