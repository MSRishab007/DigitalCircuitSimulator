package simulator.parser;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import simulator.core.Wire;
import simulator.engine.SimulationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetlistParserTest {
    @Test
    void keepsWiresInDeclarationOrder() {
        NetlistParser parser = new NetlistParser(new SimulationContext());
        parser.parseString("WIRE Zed\nWIRE Alpha\nWIRE Mid\n");

        assertEquals(java.util.List.of("Zed", "Alpha", "Mid"),
                parser.getAllWires().stream().map(Wire::getWireId).toList());
    }

    @Test
    void simTimeAfterStimulusKeepsTheStimulus() {
        SimulationContext context = new SimulationContext();
        NetlistParser parser = new NetlistParser(context);
        parser.parseString("WIRE A\nSTIMULUS 0 A 1\nSIM_TIME 100\n");

        assertEquals(1, context.getEventEngine().getPendingEventCount());
        assertEquals(100, context.getEventEngine().getMaxSimulationTime());
    }

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
