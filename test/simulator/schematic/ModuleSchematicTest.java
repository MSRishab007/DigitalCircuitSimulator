package simulator.schematic;

import org.junit.jupiter.api.Test;
import simulator.components.Module;
import simulator.engine.SimulationContext;
import simulator.parser.NetlistParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleSchematicTest {
    private static final String NESTED = "DEF MY_HALF A B S C\nXOR x 1 A B S\nAND a 1 A B C\nENDDEF\n"
            + "DEF MY_FULL A B I S C\nWIRE S1\nWIRE C1\nWIRE C2\nMY_HALF h1 A B S1 C1\nMY_HALF h2 S1 I S C2\nOR o 1 C1 C2 C\nENDDEF\n"
            + "WIRE A\nWIRE B\nWIRE I\nWIRE S\nWIRE C\nWIRE N\nMY_FULL f A B I S C\nNOT after 1 C N\n";

    private static NetlistParser parse(String netlist) {
        NetlistParser parser = new NetlistParser(new SimulationContext());
        parser.parseString(netlist);
        return parser;
    }

    private static SchematicLayout.Node node(SchematicLayout layout, String id) {
        return layout.getNodes().stream().filter(candidate -> candidate.id.equals(id)).findFirst().orElseThrow();
    }

    @Test
    void macrosBecomeNestedModules() {
        List<Module> modules = parse(NESTED).getModules();

        assertEquals(1, modules.size());
        assertEquals("MY_FULL", modules.get(0).getTypeName());
        assertEquals(3, modules.get(0).getSubComponents().size());
        assertTrue(modules.get(0).getSubComponents().get(0) instanceof Module);
    }

    @Test
    void collapsingDrawsOneBoxWithAPinForEachCrossingWire() {
        SchematicLayout layout = SchematicLayout.build(parse(NESTED), true);
        SchematicLayout.Node module = node(layout, "f");

        assertEquals(SchematicLayout.Kind.MODULE, module.kind);
        assertEquals(3, module.inputCount);
        assertEquals(2, module.outputCount);
        assertEquals(5, module.members.size());
        assertTrue(layout.getNodes().stream().noneMatch(candidate -> candidate.id.equals("f_o")));
        assertTrue(layout.getNets().stream().noneMatch(net -> net.wire.equals("f_S1") || net.wire.contains("C1")));
    }

    @Test
    void eachOutputLeavesFromItsOwnPin() {
        SchematicLayout layout = SchematicLayout.build(parse(NESTED), true);
        SchematicLayout.Node module = node(layout, "f");
        List<Integer> starts = layout.getNets().stream().filter(net -> net.wire.equals("S") || net.wire.equals("C"))
                .map(net -> net.segments.stream().filter(s -> s.x1 == module.x + module.width).findFirst().orElseThrow().y1)
                .toList();

        assertEquals(2, starts.size());
        assertFalse(starts.get(0).equals(starts.get(1)));
        assertTrue(starts.contains(module.outputY(0)));
        assertTrue(starts.contains(module.outputY(1)));
    }

    @Test
    void theFlatViewStaysTheDefault() {
        assertTrue(SchematicLayout.build(parse(NESTED)).getNodes().stream()
                .noneMatch(candidate -> candidate.kind == SchematicLayout.Kind.MODULE));
    }

    @Test
    void marksTheCriticalPathInBothViews() {
        for (boolean collapse : new boolean[]{false, true}) {
            SchematicLayout layout = SchematicLayout.build(parse(NESTED), collapse);

            assertFalse(layout.getCriticalPath().isEmpty());
            assertTrue(node(layout, "after").onCriticalPath);
            assertTrue(layout.getNets().stream().anyMatch(net -> net.onCriticalPath));
        }
    }
}
