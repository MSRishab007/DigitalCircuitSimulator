package simulator.engine;

import org.junit.jupiter.api.Test;
import simulator.components.AndGate;
import simulator.core.VoltageState;
import simulator.core.Wire;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationContextTest {

    @Test
    void createsIndependentEventEngines() {
        SimulationContext first = new SimulationContext();
        SimulationContext second = new SimulationContext();

        first.getEventEngine().setMaxSimulationTime(25);

        assertEquals(25, first.getEventEngine().getMaxSimulationTime());
        assertEquals(1000, second.getEventEngine().getMaxSimulationTime());
    }

    @Test
    void injectedContextDrivesComponentWithoutUsingSingletonEngine() {
        EventEngine singleton = EventEngine.getInstance();
        singleton.resetEngine();
        SimulationContext context = new SimulationContext();
        Wire inputA = new Wire("A");
        Wire inputB = new Wire("B");
        Wire output = new Wire("OUT");
        AndGate gate = new AndGate("AND1", 3);
        gate.setSimulationContext(context);
        inputA.addObserver(gate.getInputPin(0));
        inputB.addObserver(gate.getInputPin(1));
        gate.getOutputPin(0).connect(output);

        context.getEventEngine().scheduleEvent(
                new SignalEvent(0, inputA, VoltageState.HIGH));
        context.getEventEngine().scheduleEvent(
                new SignalEvent(0, inputB, VoltageState.HIGH));
        context.getEventEngine().runSimulation();

        assertEquals(VoltageState.HIGH, output.getCurrentState());
        assertEquals(3, context.getEventEngine().getCurrentTime());
        assertEquals(0, singleton.getTotalEventsProcessed());
    }
}
