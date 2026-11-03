package simulator.core;

public interface SignalObserver {
    void update(Wire source, VoltageState newState);
}