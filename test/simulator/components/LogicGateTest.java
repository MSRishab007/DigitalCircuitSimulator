package simulator.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.EventEngine;
import simulator.engine.SignalEvent;

import static org.junit.jupiter.api.Assertions.*;

class LogicGateTest {

    private EventEngine engine;
    private Wire inputA;
    private Wire inputB;
    private Wire output;

    @BeforeEach
    void setUp() {
        engine = EventEngine.getInstance();
        engine.resetEngine();
        
        inputA = new Wire("A");
        inputB = new Wire("B");
        output = new Wire("Out");
    }

    @Test
    void testAndGateLogicAndDelay() {
        AndGate andGate = new AndGate("AND1", 15); // 15ns delay
        inputA.addObserver(andGate.getInputPin(0));
        inputB.addObserver(andGate.getInputPin(1));
        andGate.getOutputPin(0).connect(output);

        // Inject HIGH into both inputs at t=0
        engine.scheduleEvent(new SignalEvent(0, inputA, VoltageState.HIGH));
        engine.scheduleEvent(new SignalEvent(0, inputB, VoltageState.HIGH));
        
        engine.runSimulation();

        assertEquals(VoltageState.HIGH, output.getCurrentState(), "1 AND 1 must equal 1");
        assertEquals(15, engine.getCurrentTime(), "Output event should occur exactly at propagation delay (15ns)");
    }

    @Test
    void testOrGateLogic() {
        OrGate orGate = new OrGate("OR1", 10);
        inputA.addObserver(orGate.getInputPin(0));
        inputB.addObserver(orGate.getInputPin(1));
        orGate.getOutputPin(0).connect(output);

        // Inject HIGH into A, LOW into B at t=0
        engine.scheduleEvent(new SignalEvent(0, inputA, VoltageState.HIGH));
        engine.scheduleEvent(new SignalEvent(0, inputB, VoltageState.LOW));
        
        engine.runSimulation();

        assertEquals(VoltageState.HIGH, output.getCurrentState(), "1 OR 0 must equal 1");
    }

    @Test
    void testNotGateLogic() {
        NotGate notGate = new NotGate("NOT1", 5);
        inputA.addObserver(notGate.getInputPin(0));
        notGate.getOutputPin(0).connect(output);

        // Inject LOW into A at t=0
        engine.scheduleEvent(new SignalEvent(0, inputA, VoltageState.LOW));
        
        engine.runSimulation();

        assertEquals(VoltageState.HIGH, output.getCurrentState(), "NOT 0 must equal 1");
        assertEquals(5, engine.getCurrentTime(), "Delay should be 5ns");
    }
}