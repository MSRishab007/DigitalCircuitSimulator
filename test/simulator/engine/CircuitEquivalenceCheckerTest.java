package simulator.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitEquivalenceCheckerTest {
    @Test
    void identifiesEquivalentImplementations() {
        String first = "WIRE A\nWIRE B\nWIRE OUT\nOR gate 0 A B OUT\n";
        String second = "WIRE A\nWIRE B\nWIRE OUT\nOR other 0 A B OUT\n";

        var result = new CircuitEquivalenceChecker().compare(
                first, second, List.of("A", "B"), List.of("OUT"));

        assertTrue(result.isEquivalent());
    }

    @Test
    void returnsCounterexampleForDifferentCircuits() {
        String first = "WIRE A\nWIRE B\nWIRE OUT\nAND gate 0 A B OUT\n";
        String second = "WIRE A\nWIRE B\nWIRE OUT\nOR other 0 A B OUT\n";

        var result = new CircuitEquivalenceChecker().compare(
                first, second, List.of("A", "B"), List.of("OUT"));

        assertFalse(result.isEquivalent());
        assertEquals(1, result.getCounterexampleRow());
    }
}
