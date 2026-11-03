package simulator.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.EventEngine;
import simulator.engine.SignalEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllLogicGatesTest {
    private EventEngine engine;
    private Wire a;
    private Wire b;
    private Wire output;

    @BeforeEach
    void setUp() {
        engine = EventEngine.getInstance();
        engine.resetEngine();
        a = new Wire("A");
        b = new Wire("B");
        output = new Wire("OUT");
    }

    @Test
    void nandProducesInvertedAnd() {
        NandGate gate = new NandGate("NAND", 1);
        connect(gate);
        scheduleInputs(VoltageState.HIGH, VoltageState.HIGH);
        engine.runSimulation();
        assertEquals(VoltageState.LOW, output.getCurrentState());
    }

    @Test
    void norProducesInvertedOr() {
        NorGate gate = new NorGate("NOR", 1);
        connect(gate);
        scheduleInputs(VoltageState.LOW, VoltageState.LOW);
        engine.runSimulation();
        assertEquals(VoltageState.HIGH, output.getCurrentState());
    }

    @Test
    void xorProducesHighForDifferentInputs() {
        XorGate gate = new XorGate("XOR", 1);
        connect(gate);
        scheduleInputs(VoltageState.HIGH, VoltageState.LOW);
        engine.runSimulation();
        assertEquals(VoltageState.HIGH, output.getCurrentState());
    }

    @Test
    void triStateBufferReleasesWhenDisabled() {
        TriStateBuffer buffer = new TriStateBuffer("BUFFER", 1);
        a.addObserver(buffer.getInputPin(0));
        b.addObserver(buffer.getInputPin(1));
        buffer.getOutputPin(0).connect(output);
        engine.scheduleEvent(new SignalEvent(0, a, VoltageState.HIGH));
        engine.scheduleEvent(new SignalEvent(0, b, VoltageState.LOW));
        engine.runSimulation();
        assertEquals(VoltageState.UNKNOWN, output.getCurrentState());
    }

    private void connect(LogicGate gate) {
        a.addObserver(gate.getInputPin(0));
        b.addObserver(gate.getInputPin(1));
        gate.getOutputPin(0).connect(output);
    }

    private void scheduleInputs(VoltageState first, VoltageState second) {
        engine.scheduleEvent(new SignalEvent(0, a, first));
        engine.scheduleEvent(new SignalEvent(0, b, second));
    }
}
