package simulator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WasmBridgeTest {
    private static final String NETLIST =
            "WIRE A\nWIRE B\nWIRE OUT\nAND gate 0 A B OUT\n";

    @Test
    void stepsThroughASimulation() {
        String netlist = NETLIST + "STIMULUS 0 A 1\nSTIMULUS 0 B 1\n";

        assertTrue(WasmBridge.debugStart(netlist).contains("\"time\":0,\"pending\":2"));
        assertTrue(WasmBridge.debugStep().contains("\"stepped\":true"));
        assertTrue(WasmBridge.debugStep().contains("\"stepped\":true"));
        assertTrue(WasmBridge.debugStep().contains("\"stepped\":true"));
        assertTrue(WasmBridge.debugStep().contains("\"stepped\":false"));
        assertTrue(WasmBridge.debugStart("WIRE A\nFOO x 1 A\n").contains("\"success\":false"));
        assertTrue(WasmBridge.debugStep().contains("No step session"));
    }

    @Test
    void describesTheSchematic() {
        String layout = WasmBridge.schematicLayout("WIRE A\nWIRE Y\nWIRE Spare\nFAULT STUCK_AT_0 A\nNOT n 1 A Y\n");

        assertTrue(layout.contains("\"kind\":\"NOT\""));
        assertTrue(layout.contains("\"unusedWires\":1"));
        assertTrue(layout.contains("\"A\":{\"driver\":null,\"fault\":\"stuck at 0\",\"feeds\":[\"n\"]}"));
        assertTrue(WasmBridge.schematicLayout("FOO").contains("\"success\":false"));
    }

    @Test
    void exportsStructuredAnalysisOperations() {
        assertTrue(WasmBridge.runSimulation(NETLIST).contains("\"success\":true"));
        assertTrue(WasmBridge.validateCircuit(NETLIST).contains("\"valid\":true"));
        assertTrue(WasmBridge.analyzeCircuit(NETLIST).contains("\"criticalPathDelay\""));
        assertTrue(WasmBridge.generateTruthTable(NETLIST, "A,B", "OUT")
                .contains("\"rows\""));
        assertTrue(WasmBridge.checkEquivalent(NETLIST, NETLIST, "A,B", "OUT")
                .contains("\"equivalent\":true"));
        assertTrue(WasmBridge.detectWires(NETLIST)
                .contains("\"inputs\":[\"A\",\"B\"],\"outputs\":[\"OUT\"]"));
    }
}
