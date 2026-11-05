package simulator.schematic;

import org.junit.jupiter.api.Test;
import simulator.engine.SimulationContext;
import simulator.parser.NetlistParser;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicLayoutTest {
    private static SchematicLayout layout(String netlist) {
        NetlistParser parser = new NetlistParser(new SimulationContext());
        parser.parseString(netlist);
        return SchematicLayout.build(parser);
    }

    private static List<String> names(SchematicLayout layout, SchematicLayout.Kind kind) {
        return layout.getNodes().stream().filter(node -> node.kind == kind)
                .map(node -> node.id).collect(Collectors.toList());
    }

    private static SchematicLayout.Node node(SchematicLayout layout, String id) {
        return layout.getNodes().stream().filter(candidate -> candidate.id.equals(id)).findFirst().orElseThrow();
    }

    private static void assertOrthogonalAndOnCanvas(SchematicLayout layout) {
        for (SchematicLayout.Net net : layout.getNets()) {
            assertFalse(net.segments.isEmpty(), net.wire + " has no drawing");
            for (SchematicLayout.Segment s : net.segments) {
                assertTrue(s.x1 == s.x2 || s.y1 == s.y2, net.wire + " has a diagonal segment");
                assertTrue(Math.min(s.x1, s.x2) >= 0 && Math.max(s.x1, s.x2) <= layout.getWidth(), net.wire);
                assertTrue(Math.min(s.y1, s.y2) >= 0 && Math.max(s.y1, s.y2) <= layout.getHeight(), net.wire);
            }
        }
    }

    @Test
    void placesInputsThenGatesThenOutputsFromLeftToRight() {
        SchematicLayout layout = layout("WIRE A\nWIRE B\nWIRE M\nWIRE Y\nAND g1 1 A B M\nNOT g2 1 M Y\n");

        assertEquals(List.of("A", "B"), names(layout, SchematicLayout.Kind.INPUT));
        assertEquals(List.of("Y"), names(layout, SchematicLayout.Kind.OUTPUT));
        assertTrue(node(layout, "A").x < node(layout, "g1").x);
        assertTrue(node(layout, "g1").x < node(layout, "g2").x);
        assertTrue(node(layout, "g2").x < node(layout, "Y").x);
        assertOrthogonalAndOnCanvas(layout);
    }

    @Test
    void keepsNodesInAColumnFromOverlapping() {
        SchematicLayout layout = layout("WIRE A\nWIRE B\nWIRE C\nWIRE D\nWIRE X\nWIRE Y\nWIRE Z\n"
                + "AND g1 1 A B X\nOR g2 1 C D Y\nXOR g3 1 X Y Z\n");

        SchematicLayout.Node first = node(layout, "g1");
        SchematicLayout.Node second = node(layout, "g2");
        assertEquals(first.x, second.x);
        assertTrue(Math.abs(first.y - second.y) >= (first.height + second.height) / 2);
    }

    @Test
    void routesFlipFlopFeedbackAroundTheCircuit() {
        SchematicLayout layout = layout("WIRE C\nWIRE D\nWIRE Q\nCLOCK clk 10 C\nDFF ff 1 D C Q\nNOT n 1 Q D\n");

        assertEquals(List.of("clk"), names(layout, SchematicLayout.Kind.CLOCK));
        assertEquals(List.of("ff"), names(layout, SchematicLayout.Kind.DFF));
        assertOrthogonalAndOnCanvas(layout);
        int lowestNode = layout.getNodes().stream().mapToInt(n -> n.y + n.height / 2).max().orElse(0);
        boolean usesLane = layout.getNets().stream().flatMap(net -> net.segments.stream())
                .anyMatch(s -> s.y1 == s.y2 && s.y1 > lowestNode);
        assertTrue(usesLane);
    }

    @Test
    void drawsAWireWithTwoDriversThroughAJunction() {
        SchematicLayout layout = layout("WIRE A\nWIRE E\nWIRE F\nWIRE BUS\nWIRE Y\n"
                + "BUFFER b1 1 A E BUS\nBUFFER b2 1 A F BUS\nNOT n 1 BUS Y\n");

        assertEquals(List.of("BUS"), names(layout, SchematicLayout.Kind.BUS));
        assertEquals("b1, b2", layout.getDriver("BUS"));
        assertOrthogonalAndOnCanvas(layout);
    }

    @Test
    void drawsCombinationalLoopsWithoutFailing() {
        SchematicLayout layout = layout("WIRE A\nWIRE B\nNOT n1 1 A B\nNOT n2 1 B A\n");

        assertEquals(2, layout.getNodes().size());
        assertOrthogonalAndOnCanvas(layout);
    }

    @Test
    void reportsUnconnectedWiresAndFaults() {
        SchematicLayout layout = layout("WIRE A\nWIRE Y\nWIRE Spare\nFAULT STUCK_AT_0 A\nNOT n 1 A Y\n");

        assertEquals(1, layout.getUnusedWires());
        assertEquals("stuck at 0", layout.getFault("A"));
    }

    @Test
    void expandsMacrosIntoTheirGates() {
        SchematicLayout layout = layout("DEF INV IN OUT\nWIRE mid\nNOT a 1 IN mid\nNOT b 1 mid OUT\nENDDEF\n"
                + "WIRE A\nWIRE Y\nINV u1 A Y\n");

        assertEquals(List.of("u1_a", "u1_b"), names(layout, SchematicLayout.Kind.NOT));
        assertOrthogonalAndOnCanvas(layout);
    }
}
