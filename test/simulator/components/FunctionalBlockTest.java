package simulator.components;

import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.engine.SimulationContext;
import simulator.engine.TruthTable;
import simulator.engine.TruthTableGenerator;
import simulator.parser.NetlistParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionalBlockTest {
    private static VoltageState evaluate(String expression, VoltageState a, VoltageState b) {
        return BooleanExpression.parse(expression, List.of("A", "B")).evaluate(new VoltageState[]{a, b});
    }

    @Test
    void appliesOperatorPrecedence() {
        assertEquals(VoltageState.HIGH, evaluate("A | B & 0", VoltageState.HIGH, VoltageState.LOW));
        assertEquals(VoltageState.LOW, evaluate("!A | B", VoltageState.HIGH, VoltageState.LOW));
        assertEquals(VoltageState.HIGH, evaluate("(A ^ B) & 1", VoltageState.HIGH, VoltageState.LOW));
    }

    @Test
    void knownValuesDecideDespiteUnknownInputs() {
        assertEquals(VoltageState.LOW, evaluate("A & B", VoltageState.UNKNOWN, VoltageState.LOW));
        assertEquals(VoltageState.HIGH, evaluate("A | B", VoltageState.UNKNOWN, VoltageState.HIGH));
        assertEquals(VoltageState.UNKNOWN, evaluate("A ^ B", VoltageState.UNKNOWN, VoltageState.HIGH));
        assertEquals(VoltageState.UNKNOWN, evaluate("!A", VoltageState.HIGH_Z, VoltageState.LOW));
    }

    @Test
    void reportsBadExpressions() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> evaluate("A & C", VoltageState.LOW, VoltageState.LOW))
                .getMessage().contains("C"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> evaluate("(A", VoltageState.LOW, VoltageState.LOW))
                .getMessage().contains(")"));
        assertThrows(IllegalArgumentException.class, () -> evaluate("A B", VoltageState.LOW, VoltageState.LOW));
        assertThrows(IllegalArgumentException.class, () -> evaluate("A &", VoltageState.LOW, VoltageState.LOW));
    }

    @Test
    void worksInANetlistAndInsideMacros() {
        String netlist = "DEF NAND_F X Y OUT\nFUNC f 5 X Y OUT = !(X & Y)\nENDDEF\n"
                + "WIRE A\nWIRE B\nWIRE R\nNAND_F u A B R # inline comment\n";

        TruthTable table = new TruthTableGenerator().generate(netlist, List.of("A", "B"), List.of("R"));

        assertEquals(List.of("1"), table.getRows().get(0).getOutputStates());
        assertEquals(List.of("0"), table.getRows().get(3).getOutputStates());
    }

    @Test
    void constantFunctionDrivesItsOutputAfterItsDelay() {
        SimulationContext context = new SimulationContext();
        NetlistParser parser = new NetlistParser(context);
        parser.parseString("WIRE Y\nFUNC tie 3 Y = 1\n");

        context.runSimulation();

        assertEquals(VoltageState.HIGH, parser.getWire("Y").getCurrentState());
        assertEquals(3, context.getEventEngine().getCurrentTime());
    }

    @Test
    void macrosCannotUseThemselves() {
        RuntimeException error = assertThrows(RuntimeException.class, () ->
                new NetlistParser(new SimulationContext()).parseString(
                        "DEF LOOP A\nLOOP inner A\nENDDEF\nWIRE W\nLOOP top W\n"));

        assertTrue(error.getMessage().contains("uses itself"));
    }
}
