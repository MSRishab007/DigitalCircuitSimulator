package simulator;

import org.teavm.jso.JSExport;
import simulator.application.ExampleCircuits;
import simulator.application.SimulationApplicationService;
import simulator.application.UserGuide;
import simulator.components.Clock;
import simulator.core.DigitalComponent;
import simulator.engine.CircuitEquivalenceChecker;
import simulator.engine.FaultCoverageAnalyzer;
import simulator.engine.SimulationAssertion;
import simulator.engine.SimulationContext;
import simulator.engine.SimulationDebugger;
import simulator.engine.TimingAnalysisResult;
import simulator.engine.TruthTable;
import simulator.io.VcdLogger;
import simulator.parser.NetlistParser;
import simulator.parser.TopologyValidator;
import simulator.schematic.SchematicLayout;
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
                        + result.getMaxClockFrequencyMHz() + ",\"criticalPath\":"
                        + listJson(result.getCriticalPath()) + ",\"resources\":\""
                        + escapeJson(SERVICE.resources(netlist).describe()) + "\"}";
            } catch (Exception e) {
                return errorJson(e);
            }
        }

    @JSExport
    public static String faultCoverage(String netlist) {
            try {
                FaultCoverageAnalyzer.Result result = SERVICE.faultCoverage(netlist);
                return "{\"success\":true,\"total\":" + result.getTotalFaults()
                        + ",\"detected\":" + result.getDetectedFaults()
                        + ",\"percent\":" + result.getCoveragePercent()
                        + ",\"undetected\":" + listJson(result.getUndetected())
                        + ",\"report\":\"" + escapeJson(result.describe()) + "\"}";
            } catch (Exception e) {
                return errorJson(e);
            }
        }

    @JSExport
    public static String userGuide() {
            return UserGuide.html(UserGuide.Surface.BROWSER);
        }

    @JSExport
    public static String exampleCircuits() {
            StringBuilder json = new StringBuilder("{\"success\":true,\"examples\":[");
            String separator = "";
            for (String name : ExampleCircuits.names()) {
                json.append(separator).append("{\"name\":\"").append(escapeJson(name))
                        .append("\",\"netlist\":\"").append(escapeJson(ExampleCircuits.netlist(name))).append("\"}");
                separator = ",";
            }
            return json.append("]}").toString();
        }

    private static SimulationDebugger debugger;
    private static VcdLogger debugLogger;
    private static SimulationContext debugContext;

    /** Starts a step-through session at time 0, replacing any earlier session. */
    @JSExport
    public static String debugStart(String netlist) {
            try {
                SimulationContext context = new SimulationContext();
                NetlistParser parser = new NetlistParser(context);
                parser.parseString(netlist);
                if (!new TopologyValidator().validateNoCombinationalLoops(parser.getComponents())) {
                    throw new IllegalStateException("Unclocked combinational loop detected");
                }
                String conflict = new TopologyValidator().findDriverConflict(parser.getComponents());
                if (conflict != null) {
                    throw new IllegalStateException(conflict);
                }
                VcdLogger logger = new VcdLogger(null, context);
                parser.getAllWires().forEach(logger::monitorWire);
                for (DigitalComponent component : parser.getComponents()) {
                    if (component instanceof Clock) {
                        ((Clock) component).generateClockPulses();
                    }
                }
                debugLogger = logger;
                debugContext = context;
                debugger = new SimulationDebugger(context);
                return debugJson(true);
            } catch (Exception e) {
                debugger = null;
                debugLogger = null;
                return errorJson(e);
            }
        }

    /** Processes one event. The stepped field is false once nothing is left to process. */
    @JSExport
    public static String debugStep() {
            try {
                if (debugger == null) {
                    throw new IllegalStateException("No step session is running");
                }
                return debugJson(debugger.step());
            } catch (Exception e) {
                return errorJson(e);
            }
        }

    private static String debugJson(boolean stepped) {
            return "{\"success\":true,\"stepped\":" + stepped
                    + ",\"time\":" + debugger.getCurrentTime()
                    + ",\"pending\":" + debugger.getPendingEventCount()
                    + ",\"assertions\":" + assertionLines(stepped)
                    + ",\"waveform\":\"" + escapeJson(debugLogger.toVcd()) + "\"}";
        }

    private static String assertionLines(boolean stepped) {
            List<String> lines = new java.util.ArrayList<>();
            if (!stepped) {
                for (SimulationAssertion assertion : debugContext.getAssertions()) {
                    lines.add(assertion.describe());
                }
            }
            return listJson(lines);
        }

    @JSExport
    public static String schematicLayout(String netlist) {
            return schematic(netlist, false);
        }

    @JSExport
    public static String schematicLayoutCollapsed(String netlist) {
            return schematic(netlist, true);
        }

    private static String schematic(String netlist, boolean collapseMacros) {
            try {
                NetlistParser parser = new NetlistParser(new SimulationContext());
                parser.parseString(netlist);
                return schematicJson(SchematicLayout.build(parser, collapseMacros));
            } catch (Exception e) {
                return errorJson(e);
            }
        }

    private static String schematicJson(SchematicLayout layout) {
            StringBuilder json = new StringBuilder("{\"success\":true,\"width\":").append(layout.getWidth())
                    .append(",\"height\":").append(layout.getHeight())
                    .append(",\"unusedWires\":").append(layout.getUnusedWires())
                    .append(",\"criticalPath\":").append(listJson(layout.getCriticalPath()))
                    .append(",\"nodes\":[");
            String separator = "";
            for (SchematicLayout.Node node : layout.getNodes()) {
                json.append(separator).append("{\"kind\":\"").append(node.kind.name())
                        .append("\",\"id\":\"").append(escapeJson(node.id))
                        .append("\",\"detail\":\"").append(escapeJson(node.detail))
                        .append("\",\"x\":").append(node.x).append(",\"y\":").append(node.y)
                        .append(",\"width\":").append(node.width).append(",\"height\":").append(node.height)
                        .append(",\"typeName\":\"").append(escapeJson(node.typeName))
                        .append("\",\"critical\":").append(node.onCriticalPath)
                        .append(",\"inputs\":[");
                for (int pin = 0; pin < node.inputCount; pin++) {
                    json.append(pin == 0 ? "" : ",").append(node.inputY(pin));
                }
                json.append("],\"outputs\":[");
                for (int pin = 0; pin < node.outputCount; pin++) {
                    json.append(pin == 0 ? "" : ",").append(node.outputY(pin));
                }
                json.append("]}");
                separator = ",";
            }
            json.append("],\"nets\":[");
            separator = "";
            java.util.Set<String> wires = new java.util.LinkedHashSet<>();
            for (SchematicLayout.Net net : layout.getNets()) {
                wires.add(net.wire);
                json.append(separator).append("{\"wire\":\"").append(escapeJson(net.wire))
                        .append("\",\"critical\":").append(net.onCriticalPath).append(",\"segments\":[");
                String inner = "";
                for (SchematicLayout.Segment s : net.segments) {
                    json.append(inner).append('[').append(s.x1).append(',').append(s.y1).append(',')
                            .append(s.x2).append(',').append(s.y2).append(']');
                    inner = ",";
                }
                json.append("],\"junctions\":[");
                inner = "";
                for (SchematicLayout.Point p : net.junctions) {
                    json.append(inner).append('[').append(p.x).append(',').append(p.y).append(']');
                    inner = ",";
                }
                json.append("],\"label\":");
                if (net.labelAt == null) {
                    json.append("null");
                } else {
                    json.append('[').append(net.labelAt.x).append(',').append(net.labelAt.y).append(']');
                }
                json.append('}');
                separator = ",";
            }
            json.append("],\"wires\":{");
            separator = "";
            for (String wire : wires) {
                json.append(separator).append('"').append(escapeJson(wire)).append("\":{\"driver\":")
                        .append(jsonOrNull(layout.getDriver(wire))).append(",\"fault\":")
                        .append(jsonOrNull(layout.getFault(wire))).append(",\"feeds\":")
                        .append(listJson(layout.getFeeds(wire))).append('}');
                separator = ",";
            }
            return json.append("}}").toString();
        }

    private static String jsonOrNull(String value) {
            return value == null ? "null" : '"' + escapeJson(value) + '"';
        }

    @JSExport
    public static String detectWires(String netlist) {
            try {
                var ports = SERVICE.detectPorts(netlist);
                return "{\"success\":true,\"wires\":" + listJson(ports.getWires())
                        + ",\"inputs\":" + listJson(ports.getInputs())
                        + ",\"outputs\":" + listJson(ports.getOutputs())
                        + ",\"state\":" + listJson(ports.getStateWires())
                        + ",\"sequential\":" + ports.isSequential() + "}";
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