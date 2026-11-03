package simulator.parser;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationFacadeTest {
    @Test
    void executesNetlistAndWritesRequestedWaveform() throws Exception {
        Path netlist = Files.createTempFile("facade", ".netlist");
        Path waveform = Files.createTempFile("facade", ".vcd");
        Files.writeString(netlist, "SIM_TIME 10\nWIRE A\nSTIMULUS 0 A 1\n");

        new SimulationFacade().executeSimulation(netlist.toString(), waveform.toString());

        assertTrue(Files.size(waveform) > 0);
        Files.deleteIfExists(netlist);
        Files.deleteIfExists(waveform);
    }
}
