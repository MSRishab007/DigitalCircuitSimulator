package simulator;

import org.teavm.jso.JSExport;
import simulator.parser.SimulationRunner;

public class WasmBridge {
    public static void main(String[] args) {
    }

    @JSExport
    public static String runSimulation(String netlist) {
        try {
            SimulationRunner.SimulationResult result =
                    new SimulationRunner().runString(netlist, null);
            return result.toJson();
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "Unknown simulation error";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}