package simulator.core;

public class InputPin implements SignalObserver {
    private VoltageState currentState;
    private final DigitalComponent parentComponent;

    public InputPin(DigitalComponent parentComponent) {
        this.parentComponent = parentComponent;
        this.currentState = VoltageState.UNKNOWN;
    }

    @Override
    public void update(Wire source, VoltageState newState) {
        this.currentState = newState;
        parentComponent.inputChanged(this, newState);
    }

    public VoltageState getCurrentState() { return currentState; }
    public DigitalComponent getParentComponent() { return parentComponent; }
}