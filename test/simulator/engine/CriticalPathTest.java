package simulator.engine;

import org.junit.jupiter.api.Test;
import simulator.parser.NetlistParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalPathTest {
    private static TimingAnalysisResult analyze(String netlist) {
        NetlistParser parser = new NetlistParser(new SimulationContext());
        parser.parseString(netlist);
        return new StaticTimingAnalyzer().analyze(parser.getComponents());
    }

    @Test
    void namesTheSlowestRoute() {
        TimingAnalysisResult result = analyze("WIRE A\nWIRE B\nWIRE C\nWIRE D\n"
                + "NOT slow 10 A B\nNOT fast 1 A C\nAND last 5 B C D\n");

        assertEquals(15, result.getCriticalPathDelay());
        assertEquals(List.of("slow", "last"), result.getCriticalPath());
    }

    @Test
    void startsAtAFlipFlopAndEndsAtTheNextOne() {
        TimingAnalysisResult result = analyze("WIRE D\nWIRE CLK\nWIRE Q1\nWIRE N\nWIRE Q2\n"
                + "DFF first 4 D CLK Q1\nNOT n 6 Q1 N\nDFF second 3 N CLK Q2\n");

        assertEquals(10, result.getCriticalPathDelay());
        assertEquals(List.of("first", "n"), result.getCriticalPath());
    }

    @Test
    void aFlipFlopFeedingItselfIsNotALoop() {
        TimingAnalysisResult result = analyze("WIRE CLK\nWIRE Q\nWIRE N\nNOT n 5 Q N\nDFF f 2 N CLK Q\n");

        assertEquals(7, result.getCriticalPathDelay());
        assertEquals(List.of("f", "n"), result.getCriticalPath());
    }

    @Test
    void theClockIsNotPartOfADataPath() {
        TimingAnalysisResult result = analyze("WIRE D\nWIRE CLK\nWIRE Q\nCLOCK c 10 CLK\nDFF f 3 D CLK Q\n");

        assertEquals(3, result.getCriticalPathDelay());
        assertEquals(List.of("f"), result.getCriticalPath());
    }

    @Test
    void aCircuitWithoutDelayHasNoPath() {
        assertTrue(analyze("WIRE A\n").getCriticalPath().isEmpty());
    }
}
