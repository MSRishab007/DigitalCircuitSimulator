package simulator.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TruthTableTest {
    private static final String AND_GATE = "WIRE A\nWIRE B\nWIRE OUT\nAND gate 0 A B OUT\n";

    @Test
    void generatesAndTruthTable() {
        TruthTable table = new TruthTableGenerator().generate(
                AND_GATE, List.of("A", "B"), List.of("OUT"));

        assertEquals(List.of("0"), table.getRows().get(0).getOutputStates());
        assertEquals(List.of("1"), table.getRows().get(3).getOutputStates());
        assertEquals(4, table.getRows().size());
    }

    @Test
    void textLinesUpValuesUnderLongWireNames() {
        String netlist = "WIRE Input_A\nWIRE Sum\nNOT inv 0 Input_A Sum\n";

        String[] lines = new TruthTableGenerator().generate(
                netlist, List.of("Input_A"), List.of("Sum")).toText().split("\n");

        assertEquals("Input_A | Sum", lines[0]);
        assertEquals("0       | 1  ", lines[1]);
        assertEquals("1       | 0  ", lines[2]);
    }

    @Test
    void exportsCsv() {
        String csv = new TruthTableGenerator().generate(
                AND_GATE, List.of("A", "B"), List.of("OUT")).toCsv();

        assertEquals("A,B,OUT\n0,0,0\n0,1,0\n1,0,0\n1,1,1\n", csv);
    }

    @Test
    void shortSimTimeDoesNotCutOffSettling() {
        String netlist = "SIM_TIME 5\n" + "WIRE A\nWIRE B\nWIRE OUT\nAND gate 10 A B OUT\n";

        TruthTable table = new TruthTableGenerator().generate(
                netlist, List.of("A", "B"), List.of("OUT"));

        assertEquals(List.of("1"), table.getRows().get(3).getOutputStates());
    }

    @Test
    void ignoresTestbenchAssertionsAndStimuli() {
        String netlist = AND_GATE + "STIMULUS\t0 A 1\nSTIMULUS 0 B 1\nASSERT 0 OUT 1\n";

        TruthTable table = new TruthTableGenerator().generate(
                netlist, List.of("A", "B"), List.of("OUT"));

        assertEquals(List.of("0"), table.getRows().get(0).getOutputStates());
    }

    @Test
    void handlesWindowsLineEndingsAndLowerCaseCommands() {
        String netlist = "sim_time 5\r\nWIRE A\r\nWIRE B\r\nWIRE OUT\r\nAND gate 10 A B OUT\r\n"
                + "stimulus 0 A 1\r\n  ASSERT 0 OUT 1\r\n";

        TruthTable table = new TruthTableGenerator().generate(
                netlist, List.of("A", "B"), List.of("OUT"));

        assertEquals(List.of("1"), table.getRows().get(3).getOutputStates());
        assertEquals(List.of("0"), table.getRows().get(0).getOutputStates());
    }

    @Test
    void explainsWhyClockedCircuitsAreRejected() {
        String netlist = "WIRE C\nWIRE D\nWIRE Q\nCLOCK clk 10 C\nDFF ff 1 D C Q\n";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new TruthTableGenerator().generate(netlist, List.of("D"), List.of("Q")));

        assertTrue(error.getMessage().contains("combinational"));
    }

    @Test
    void rejectsAnInputThatAGateAlreadyDrives() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new TruthTableGenerator().generate(AND_GATE, List.of("OUT"), List.of("A")));

        assertTrue(error.getMessage().contains("already driven by gate"));
    }

    @Test
    void reportsParseErrorsOnTheOriginalLineNumber() {
        String netlist = "SIM_TIME 10\nWIRE A\nWIRE OUT\nFOO g 1 A OUT\n";

        RuntimeException error = assertThrows(RuntimeException.class, () ->
                new TruthTableGenerator().generate(netlist, List.of("A"), List.of("OUT")));

        assertTrue(error.getMessage().contains("line 4"));
        assertTrue(error.getMessage().contains("Unsupported component type: FOO"));
    }
}
