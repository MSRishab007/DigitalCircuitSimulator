package simulator.io;

import org.junit.jupiter.api.Test;
import simulator.core.VoltageState;
import simulator.core.Wire;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VcdLoggerTest {
    @Test
    void exportsMonitoredWireTransitions() throws Exception {
        Path output = Files.createTempFile("waveform", ".vcd");
        Wire wire = new Wire("DATA");
        VcdLogger logger = new VcdLogger(output.toString());
        logger.monitorWire(wire);

        wire.updateVoltage(VoltageState.HIGH);
        logger.exportFile();

        String content = Files.readString(output);
        assertTrue(content.contains("$timescale 1ns $end"));
        assertTrue(content.contains("$var wire 1 DATA DATA $end"));
        assertTrue(content.contains("1DATA"));
        Files.deleteIfExists(output);
    }
}
