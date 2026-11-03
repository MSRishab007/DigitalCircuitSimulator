package simulator.core;

import java.util.ArrayList;
import java.util.List;

public abstract class DigitalComponent {
    protected String componentId;
    protected List<InputPin> inputPins;
    protected List<OutputPin> outputPins;

    public DigitalComponent(String id, int numInputs, int numOutputs) {
        this.componentId = id;
        this.inputPins = new ArrayList<>();
        this.outputPins = new ArrayList<>();
        
        for (int i = 0; i < numInputs; i++) {
            inputPins.add(new InputPin(this));
        }
        for (int i = 0; i < numOutputs; i++) {
            outputPins.add(new OutputPin());
        }
    }

    public abstract void evaluate();

    public void inputChanged(InputPin pin, VoltageState newState) {
        evaluate();
    }
    
    public String getComponentId() {
        return componentId;
    }
    
    public InputPin getInputPin(int index) {
        if (index >= 0 && index < inputPins.size()) {
            return inputPins.get(index);
        }
        throw new IndexOutOfBoundsException("Invalid Input Pin Index");
    }
    
    public OutputPin getOutputPin(int index) {
        if (index >= 0 && index < outputPins.size()) {
            return outputPins.get(index);
        }
        throw new IndexOutOfBoundsException("Invalid Output Pin Index");
    }
    public List<OutputPin> getOutputPins() {
        return outputPins;
    }

    public List<InputPin> getInputPins() {
        return inputPins;
    }
}