package simulator.parser;

import simulator.components.Clock;
import simulator.core.DigitalComponent;
import simulator.engine.SimulationAssertion;
import simulator.engine.SimulationContext;
import simulator.engine.StaticTimingAnalyzer;
import simulator.engine.TimingAnalysisResult;
import simulator.io.VcdLogger;

import java.io.IOException;
import java.util.List;

public final class SimulationRunner {
    public SimulationResult runFile(String netlistFilePath, String outputWaveformPath) throws IOException {
        SimulationContext context = new SimulationContext();
        NetlistParser parser = new NetlistParser(context);
        parser.parse(netlistFilePath);
        return runParsed(parser, outputWaveformPath, context);
    }

    public SimulationResult runString(String netlist, String outputWaveformPath) {
        SimulationContext context = new SimulationContext();
        NetlistParser parser = new NetlistParser(context);
        parser.parseString(netlist);
        return runParsed(parser, outputWaveformPath, context);
    }

    private SimulationResult runParsed(NetlistParser parser, String outputWaveformPath,
                                       SimulationContext context) {
        if (!new TopologyValidator().validateNoCombinationalLoops(parser.getComponents())) {
            throw new IllegalStateException("Validation Failed: Unclocked combinational loop detected.");
        }
        String conflict = new TopologyValidator().findDriverConflict(parser.getComponents());
        if (conflict != null) {
            throw new IllegalStateException("Validation Failed: " + conflict);
        }

        StaticTimingAnalyzer analyzer = new StaticTimingAnalyzer();
        TimingAnalysisResult timing = analyzer.analyze(parser.getComponents());

        VcdLogger logger = new VcdLogger(outputWaveformPath, context);
        parser.getAllWires().forEach(logger::monitorWire);

        for (DigitalComponent component : parser.getComponents()) {
            if (component instanceof Clock) {
                ((Clock) component).generateClockPulses();
            }
        }

        var engine = context.getEventEngine();
        context.runSimulation();

        if (outputWaveformPath != null) {
            try {
                logger.exportFile();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to export waveform: " + outputWaveformPath, e);
            }
        }

        return new SimulationResult(
                parser.getAllWires().size(),
                parser.getComponents().size(),
                timing.getCriticalPathDelay(),
                engine.getCurrentTime(),
                engine.getTotalEventsProcessed(),
                engine.getPeakQueueDepth(),
                engine.getComputeTimeMillis(),
                logger.toVcd(),
                timing.getCriticalPath(),
                context.getAssertions());
    }

    public static final class SimulationResult {
        private final int wireCount;
        private final int componentCount;
        private final int criticalPathDelay;
        private final long simulatedTime;
        private final long eventsProcessed;
        private final int peakQueueDepth;
        private final long computeTimeMillis;
        private final String waveform;
        private final List<String> criticalPath;
        private final List<SimulationAssertion> assertions;

        private SimulationResult(int wireCount, int componentCount, int criticalPathDelay,
                                 long simulatedTime, long eventsProcessed, int peakQueueDepth,
                                 long computeTimeMillis, String waveform,
                                 List<String> criticalPath, List<SimulationAssertion> assertions) {
            this.wireCount = wireCount;
            this.componentCount = componentCount;
            this.criticalPathDelay = criticalPathDelay;
            this.simulatedTime = simulatedTime;
            this.eventsProcessed = eventsProcessed;
            this.peakQueueDepth = peakQueueDepth;
            this.computeTimeMillis = computeTimeMillis;
            this.waveform = waveform;
            this.criticalPath = criticalPath;
            this.assertions = assertions;
        }

        public int getWireCount() { return wireCount; }
        public int getComponentCount() { return componentCount; }
        public int getCriticalPathDelay() { return criticalPathDelay; }
        public long getSimulatedTime() { return simulatedTime; }
        public long getEventsProcessed() { return eventsProcessed; }
        public int getPeakQueueDepth() { return peakQueueDepth; }
        public long getComputeTimeMillis() { return computeTimeMillis; }
        public String getWaveform() { return waveform; }
        public List<String> getCriticalPath() { return criticalPath; }
        public List<SimulationAssertion> getAssertions() { return assertions; }

        public int getFailedAssertionCount() {
            int failed = 0;
            for (SimulationAssertion assertion : assertions) {
                failed += assertion.hasPassed() ? 0 : 1;
            }
            return failed;
        }

        public String toJson() {
            return "{\"success\":true,\"wireCount\":" + wireCount
                    + ",\"componentCount\":" + componentCount
                    + ",\"criticalPathDelay\":" + criticalPathDelay
                    + ",\"simulatedTime\":" + simulatedTime
                    + ",\"eventsProcessed\":" + eventsProcessed
                    + ",\"peakQueueDepth\":" + peakQueueDepth
                    + ",\"computeTimeMillis\":" + computeTimeMillis
                    + ",\"waveform\":\"" + escapeJson(waveform) + "\""
                    + ",\"criticalPath\":" + stringsJson(criticalPath)
                    + ",\"failedAssertions\":" + getFailedAssertionCount()
                    + ",\"assertions\":" + assertionsJson(assertions) + "}";
        }

        private static String stringsJson(List<String> values) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < values.size(); i++) {
                json.append(i == 0 ? "\"" : ",\"").append(escapeJson(values.get(i))).append('"');
            }
            return json.append(']').toString();
        }

        public static String assertionsJson(List<SimulationAssertion> assertions) {
            List<String> lines = new java.util.ArrayList<>();
            for (SimulationAssertion assertion : assertions) {
                lines.add(assertion.describe());
            }
            return stringsJson(lines);
        }

        private static String escapeJson(String value) {
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
        }
    }
}
