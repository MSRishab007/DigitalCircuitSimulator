package simulator;

import org.teavm.jso.JSExport;
import simulator.application.SimulationApplicationService;
import simulator.engine.CircuitEquivalenceChecker;
import simulator.engine.TimingAnalysisResult;
import simulator.engine.TruthTable;
import java.util.Arrays;
import java.util.List;

public class WasmBridge {
    private static final SimulationApplicationService SERVICE =
            new SimulationApplicationService();
    public static void main(String[] args) {
    }

    @JSExport
    public static String runSimulation(String netlist) {
        try {
            var result = SERVICE.simulate(netlist, null);
            return result.toJson();
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    @JSExport
    public static String validateCircuit(String netlist) {
            try {
                var result = SERVICE.validate(netlist);
                return "{\"success\":true,\"valid\":" + result.isValid()
                        + ",\"message\":\"" + escapeJson(result.getMessage()) + "\"}";
            } catch (Exception e) {
                return errorJson(e);
            }
        }

    @JSExport
    public static String analyzeCircuit(String netlist) {
            try {
                TimingAnalysisResult result = SERVICE.analyze(netlist);
                return "{\"success\":true,\"criticalPathDelay\":"
                        + result.getCriticalPathDelay() + ",\"maxClockFrequencyMHz\":"
                        + result.getMaxClockFrequencyMHz() + "}";
            } catch (Exception e) {
                return errorJson(e);
            }
        }

    @JSExport
    public static String generateTruthTable(String netlist, String inputs,
                                            String outputs) {
            try {
                TruthTable table = SERVICE.truthTable(netlist, names(inputs), names(outputs));
                return truthTableJson(table);
            } catch (Exception e) {
                return errorJson(e);
            }
        }

    @JSExport
    public static String checkEquivalent(String firstNetlist, String secondNetlist,
                                         String inputs, String outputs) {
            try {
                var result = SERVICE.equivalent(firstNetlist, secondNetlist,
                        names(inputs), names(outputs));
                String json = "{\"success\":true,\"equivalent\":" + result.isEquivalent();
                if (!result.isEquivalent()) {
                    json += ",\"counterexampleRow\":" + result.getCounterexampleRow()
                            + ",\"inputs\":" + listJson(result.getFirstRow().getInputStates())
                            + ",\"firstOutputs\":" + listJson(result.getFirstRow().getOutputStates())
                            + ",\"secondOutputs\":" + listJson(result.getSecondRow().getOutputStates());
                }
                return json + "}";
            } catch (Exception e) {
                return errorJson(e);
            }
        }

    private static List<String> names(String value) {
            return Arrays.stream(value.split(",")).map(String::trim)
                    .filter(name -> !name.isEmpty()).toList();
        }

    private static String truthTableJson(TruthTable table) {
            StringBuilder json = new StringBuilder("{\"success\":true,\"inputs\":")
                    .append(listJson(table.getInputs())).append(",\"outputs\":")
                    .append(listJson(table.getOutputs())).append(",\"rows\":[");
            for (int i = 0; i < table.getRows().size(); i++) {
                if (i > 0) json.append(',');
                var row = table.getRows().get(i);
                json.append("{\"inputs\":").append(listJson(row.getInputStates()))
                        .append(",\"outputs\":").append(listJson(row.getOutputStates()))
                        .append('}');
            }
            return json.append("]}").toString();
        }

    private static String listJson(List<String> values) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) json.append(',');
                json.append('"').append(escapeJson(values.get(i))).append('"');
            }
            return json.append(']').toString();
        }

    private static String errorJson(Exception exception) {
            return "{\"success\":false,\"error\":\"" + escapeJson(exception.getMessage()) + "\"}";
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