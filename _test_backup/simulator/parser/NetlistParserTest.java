package simulator.parser;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NetlistParserTest {
    @Test
    void rejectsDuplicateWires() throws Exception {
        Path netlist = Files.createTempFile("duplicate-wire", ".netlist");
        Files.writeString(netlist, "WIRE A\nWIRE A\n");

        assertThrows(RuntimeException.class, () -> new NetlistParser().parse(netlist.toString()));
        Files.deleteIfExists(netlist);
    }

    @Test
    void rejectsInvalidStimulusState() throws Exception {
        Path netlist = Files.createTempFile("invalid-stimulus", ".netlist");
        Files.writeString(netlist, "WIRE A\nSTIMULUS 0 A X\n");

        assertThrows(RuntimeException.class, () -> new NetlistParser().parse(netlist.toString()));
        Files.deleteIfExists(netlist);
    }

    @Test
    void rejectsUnclosedMacro() throws Exception {
        Path netlist = Files.createTempFile("unclosed-macro", ".netlist");
        Files.writeString(netlist, "DEF BUF IN OUT\nWIRE LOCAL\n");

        assertThrows(RuntimeException.class, () -> new NetlistParser().parse(netlist.toString()));
        Files.deleteIfExists(netlist);
    }
}
