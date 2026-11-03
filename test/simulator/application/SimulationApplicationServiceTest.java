package simulator.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationApplicationServiceTest {
    private final SimulationApplicationService service =
            new SimulationApplicationService();

    @Test
    void exposesAllCoreOperationsThroughOneBoundary() {
        String netlist = "WIRE A\nWIRE B\nWIRE OUT\nAND gate 0 A B OUT\n"
                + "STIMULUS 0 A 1\nSTIMULUS 0 B 1\n";

        assertTrue(service.simulate(netlist, null).getEventsProcessed() > 0);
        assertTrue(service.validate(netlist).isValid());
        assertEquals(0, service.analyze(netlist).getCriticalPathDelay());
        assertEquals(4, service.truthTable(netlist, List.of("A", "B"),
                List.of("OUT")).getRows().size());
        assertTrue(service.equivalent(netlist, netlist, List.of("A", "B"),
                List.of("OUT")).isEquivalent());
    }
}
