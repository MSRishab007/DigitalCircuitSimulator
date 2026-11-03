package simulator.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ClockTest {
    @Test
    void rejectsPeriodsThatCannotProduceAValidHalfPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new Clock("CLK", 1));
        assertThrows(IllegalArgumentException.class, () -> new Clock("CLK", 0));
        assertThrows(IllegalArgumentException.class, () -> new Clock("CLK", -2));
    }
}
