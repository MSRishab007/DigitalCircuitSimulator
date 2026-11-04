package simulator.parser;

import simulator.application.SimulationApplicationService;
import java.nio.file.Files;
import java.nio.file.Path;

public class SimulationFacade {
    private final SimulationApplicationService service =
            new SimulationApplicationService();

    public void executeSimulation(String netlistFilePath, String outputWaveformPath) throws Exception {
        System.out.println("1. Loading netlist: " + netlistFilePath);
        String netlist = Files.readString(Path.of(netlistFilePath));
        System.out.println("2. Validating topology...");
        System.out.println("3. Initializing simulation clock at zero...");
        System.out.println("4. Executing discrete event loop...");
        System.out.println("5. Generating output waveform...");
        SimulationRunner.SimulationResult result = service.simulate(netlist, outputWaveformPath);
        System.out.println("Simulation complete. Processed " + result.getEventsProcessed() + " events.");
        System.out.println("Waveform written to " + outputWaveformPath);
        if (result.getEventsProcessed() == 0) {
            System.out.println("Warning: this netlist has no STIMULUS lines or CLOCK, "
                    + "so every wire stays X.");
        }
    }
}