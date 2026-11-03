package simulator.parser;

import simulator.core.DigitalComponent;
import simulator.engine.EventEngine;
import simulator.io.VcdLogger;

import java.util.ArrayList;
import java.util.List;

public class SimulationFacade {
    private final TopologyValidator validator;
    private final EventEngine engine;
    private final List<DigitalComponent> activeComponents;
    private VcdLogger logger;

    public SimulationFacade() {
        this.validator = new TopologyValidator();
        this.engine = EventEngine.getInstance();
        this.activeComponents = new ArrayList<>();
    }

    public void executeSimulation(String netlistFilePath, String outputWaveformPath) {
        System.out.println("1. Loading netlist: " + netlistFilePath);
        // Phase 1: The parser reads the external configuration file and triggers object creation[cite: 5, 12].
        // (Mock parsing step - you would read the file and use ComponentFactory here)
        
        System.out.println("2. Validating topology...");
        // Phase 2: The software validates the circuit topology to prevent illegal continuous feedback loops[cite: 5, 12].
        boolean isValid = validator.validateNoCombinationalLoops(activeComponents);
        if (!isValid) {
            throw new IllegalStateException("Validation Failed: Unclocked combinational loop detected.");
        }

        System.out.println("3. Initializing simulation clock at zero...");
        // Phase 3: The simulation clock initializes at zero[cite: 10].
        engine.resetEngine();
        
        logger = new VcdLogger(outputWaveformPath);
        // (Attach logger to wires here)

        System.out.println("4. Executing discrete event loop...");
        // Phase 4: The engine processes queued signal events sequentially[cite: 5, 10].
        engine.runSimulation();

        System.out.println("5. Generating output waveform...");
        // Phase 5: The software returns the generated waveform file[cite: 10].
        logger.exportFile();
        
        System.out.println("Simulation complete.");
    }
}