package simulator.engine;

import org.junit.jupiter.api.Test;
import simulator.application.SimulationApplicationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CircuitPortsTest {
    @Test
    void findsInputsAndOutputsInDeclarationOrder() {
        String netlist = "WIRE Zed\nWIRE Alpha\nWIRE Mid\nWIRE Out\nWIRE Unused\n"
                + "AND g1 1 Zed Alpha Mid\nNOT g2 1 Mid Out\n";

        CircuitPorts ports = new SimulationApplicationService().detectPorts(netlist);

        assertEquals(List.of("Zed", "Alpha"), ports.getInputs());
        assertEquals(List.of("Out"), ports.getOutputs());
        assertEquals(List.of("Zed", "Alpha", "Mid", "Out", "Unused"), ports.getWires());
    }

    @Test
    void seesThroughMacros() {
        String netlist = "DEF INV IN OUT\nWIRE mid\nNOT a 1 IN mid\nNOT b 1 mid OUT\nENDDEF\n"
                + "WIRE A\nWIRE Y\nINV u1 A Y\n";

        CircuitPorts ports = new SimulationApplicationService().detectPorts(netlist);

        assertEquals(List.of("A"), ports.getInputs());
        assertEquals(List.of("Y"), ports.getOutputs());
    }
}
