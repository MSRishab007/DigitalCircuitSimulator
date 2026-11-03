package simulator.parser;

import simulator.components.Clock;
import simulator.engine.EventEngine;
import simulator.io.VcdLogger;

public class SimulationFacade {
    private final TopologyValidator validator;
    private final EventEngine engine;
    private VcdLogger logger;

    public SimulationFacade() {
        this.validator = new TopologyValidator();
        this.engine = EventEngine.getInstance();
    }

    public void executeSimulation(String netlistFilePath, String outputWaveformPath) throws Exception {
        System.out.println("1. Loading netlist: " + netlistFilePath);
        NetlistParser parser = new NetlistParser();
        parser.parse(netlistFilePath);
        
        System.out.println("2. Validating topology...");
        boolean isValid = validator.validateNoCombinationalLoops(parser.getComponents());
        if (!isValid) {
            throw new IllegalStateException("Validation Failed: Unclocked combinational loop detected.");
        }

        System.out.println("3. Initializing simulation clock at zero...");
        engine.resetEngine();
        
        logger = new VcdLogger(outputWaveformPath);
        parser.getAllWires().forEach(logger::monitorWire);
        parser.getComponents().stream()
                .filter(Clock.class::isInstance)
                .map(Clock.class::cast)
                .forEach(Clock::generateClockPulses);

        System.out.println("4. Executing discrete event loop...");
        engine.runSimulation();

        System.out.println("5. Generating output waveform...");
        logger.exportFile();
        System.out.println("Simulation complete. Processed " + engine.getTotalEventsProcessed() + " events.");
    }
}