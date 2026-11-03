package simulator.parser;

import simulator.components.Clock;
import simulator.core.DigitalComponent;
import simulator.engine.EventEngine;
import simulator.engine.StaticTimingAnalyzer;
import simulator.io.VcdLogger;

import java.io.IOException;

public final class SimulationRunner {
    public SimulationResult runFile(String netlistFilePath, String outputWaveformPath) throws IOException {
        NetlistParser parser = new NetlistParser();
        parser.parse(netlistFilePath);
        return runParsed(parser, outputWaveformPath);
    }

    public SimulationResult runString(String netlist, String outputWaveformPath) {
        NetlistParser parser = new NetlistParser();
        parser.parseString(netlist);
        return runParsed(parser, outputWaveformPath);
    }

    private SimulationResult runParsed(NetlistParser parser, String outputWaveformPath) {
        if (!new TopologyValidator().validateNoCombinationalLoops(parser.getComponents())) {
            throw new IllegalStateException("Validation Failed: Unclocked combinational loop detected.");
        }

        StaticTimingAnalyzer analyzer = new StaticTimingAnalyzer();
        analyzer.analyze(parser.getComponents());

        VcdLogger logger = outputWaveformPath == null ? null : new VcdLogger(outputWaveformPath);
        if (logger != null) {
            parser.getAllWires().forEach(logger::monitorWire);
        }

        for (DigitalComponent component : parser.getComponents()) {
            if (component instanceof Clock) {
                ((Clock) component).generateClockPulses();
            }
        }

        EventEngine engine = EventEngine.getInstance();
        engine.runSimulation();

        if (logger != null) {
            try {
                logger.exportFile();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to export waveform: " + outputWaveformPath, e);
            }
        }

        return new SimulationResult(
                parser.getAllWires().size(),
                parser.getComponents().size(),
                analyzer.getCriticalPathDelay(),
                engine.getCurrentTime(),
                engine.getTotalEventsProcessed(),
                engine.getPeakQueueDepth(),
                engine.getComputeTimeMillis());
    }

    public static final class SimulationResult {
        private final int wireCount;
        private final int componentCount;
        private final int criticalPathDelay;
        private final long simulatedTime;
        private final long eventsProcessed;
        private final int peakQueueDepth;
        private final long computeTimeMillis;

        private SimulationResult(int wireCount, int componentCount, int criticalPathDelay,
                                 long simulatedTime, long eventsProcessed, int peakQueueDepth,
                                 long computeTimeMillis) {
            this.wireCount = wireCount;
            this.componentCount = componentCount;
            this.criticalPathDelay = criticalPathDelay;
            this.simulatedTime = simulatedTime;
            this.eventsProcessed = eventsProcessed;
            this.peakQueueDepth = peakQueueDepth;
            this.computeTimeMillis = computeTimeMillis;
        }

        public int getWireCount() { return wireCount; }
        public int getComponentCount() { return componentCount; }
        public int getCriticalPathDelay() { return criticalPathDelay; }
        public long getSimulatedTime() { return simulatedTime; }
        public long getEventsProcessed() { return eventsProcessed; }
        public int getPeakQueueDepth() { return peakQueueDepth; }
        public long getComputeTimeMillis() { return computeTimeMillis; }
    }
}
