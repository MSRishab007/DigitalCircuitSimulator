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
    void faultDeclaredBeforeAGateStillReachesThatGate() {
        SimulationContext context = new SimulationContext();
        NetlistParser parser = new NetlistParser(context);
        parser.parseString("WIRE A\nWIRE Y\nFAULT STUCK_AT_0 A\nNOT n 5 A Y\n");

        context.runSimulation();

        assertEquals(simulator.core.VoltageState.HIGH, parser.getWire("Y").getCurrentState());
    }

    private static String countAfter(long simTime) {
        SimulationContext context = new SimulationContext();
        NetlistParser parser = new NetlistParser(context);
        parser.parseString("SIM_TIME " + simTime + "\nWIRE CLK\nWIRE D0\nWIRE Q0\nWIRE D1\nWIRE Q1\n"
                + "CLOCK c 20 CLK\nDFF f0 2 D0 CLK Q0\nNOT n0 1 Q0 D0\n"
                + "DFF f1 2 D1 CLK Q1\nXOR x1 1 Q0 Q1 D1\n");
        parser.getComponents().stream().filter(c -> c instanceof simulator.components.Clock)
                .forEach(c -> ((simulator.components.Clock) c).generateClockPulses());
        context.runSimulation();
        return parser.getWire("Q1").getCurrentState() + "," + parser.getWire("Q0").getCurrentState();
    }

    @Test
    void feedbackThroughFlipFlopsCountsInBinary() {
        assertEquals("LOW,HIGH", countAfter(15));
        assertEquals("HIGH,LOW", countAfter(35));
        assertEquals("HIGH,HIGH", countAfter(55));
        assertEquals("LOW,LOW", countAfter(75));
    }

    @Test
    void flipFlopCapturesAFaultDeclaredBeforeIt() {
        SimulationContext context = new SimulationContext();
        NetlistParser parser = new NetlistParser(context);
        parser.parseString("SIM_TIME 100\nWIRE CLK\nWIRE D\nWIRE Q\nFAULT STUCK_AT_0 D\n"
                + "CLOCK c 20 CLK\nDFF ff 5 D CLK Q\n");
        parser.getComponents().stream().filter(c -> c instanceof simulator.components.Clock)
                .forEach(c -> ((simulator.components.Clock) c).generateClockPulses());

        context.runSimulation();

        assertEquals(simulator.core.VoltageState.LOW, parser.getWire("Q").getCurrentState());
    }

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
