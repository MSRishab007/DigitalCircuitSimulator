package simulator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WasmBridgeTest {
    private static final String NETLIST =
            "WIRE A\nWIRE B\nWIRE OUT\nAND gate 0 A B OUT\n";

    @Test
    void exportsStructuredAnalysisOperations() {
        assertTrue(WasmBridge.runSimulation(NETLIST).contains("\"success\":true"));
        assertTrue(WasmBridge.validateCircuit(NETLIST).contains("\"valid\":true"));
        assertTrue(WasmBridge.analyzeCircuit(NETLIST).contains("\"criticalPathDelay\""));
        assertTrue(WasmBridge.generateTruthTable(NETLIST, "A,B", "OUT")
                .contains("\"rows\""));
        assertTrue(WasmBridge.checkEquivalent(NETLIST, NETLIST, "A,B", "OUT")
                .contains("\"equivalent\":true"));
    }
}
