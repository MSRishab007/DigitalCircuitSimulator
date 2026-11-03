package simulator.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TruthTableTest {
    @Test
    void generatesAndTruthTable() {
        String netlist = "WIRE A\nWIRE B\nWIRE OUT\nAND gate 0 A B OUT\n";

        TruthTable table = new TruthTableGenerator().generate(
                netlist, List.of("A", "B"), List.of("OUT"));

        assertEquals(List.of("0"), table.getRows().get(0).getOutputStates());
        assertEquals(List.of("1"), table.getRows().get(3).getOutputStates());
        assertEquals(4, table.getRows().size());
    }
}
