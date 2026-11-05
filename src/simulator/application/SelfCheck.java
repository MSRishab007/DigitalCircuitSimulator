package simulator.application;

import simulator.engine.CircuitPorts;
import simulator.engine.FaultCoverageAnalyzer;
import simulator.engine.SimulationContext;
import simulator.engine.TimingAnalysisResult;
import simulator.engine.TruthTable;
import simulator.parser.NetlistParser;
import simulator.parser.SimulationRunner;
import simulator.schematic.SchematicLayout;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class SelfCheck {
    private interface Check {
        void run() throws Exception;
    }

    public static final class Result {
        public final String name;
        public final boolean passed;
        public final String detail;

        private Result(String name, boolean passed, String detail) {
            this.name = name;
            this.passed = passed;
            this.detail = detail;
        }
    }

    private static final long REQUIRED_EVENTS_PER_SECOND = 10_000;
    private final SimulationApplicationService service = new SimulationApplicationService();
    private final List<Result> results = new ArrayList<>();

    public List<Result> run() {
        check("Gate logic: AND, OR, NOT, NAND, NOR, XOR, XNOR", this::gates);
        check("Propagation delay and event timing", this::timing);
        check("Waveform export to a VCD file", this::waveform);
        check("Truth table of a full adder", this::fullAdderTable);
        check("Functional block matches its gate-level circuit", this::functionalEquivalence);
        check("Equivalence check finds a difference", this::findsDifference);
        check("Flip-flops and clock count in binary", this::counter);
        check("State table of an up-down counter", this::stateTable);
        check("Nested macros and component library", this::macros);
        check("Stuck-at fault holds a wire", this::fault);
        check("Assertion failure is reported", this::assertion);
        check("Combinational loop is rejected", this::loop);
        check("Wire with two gate drivers is rejected", this::driverConflict);
        check("Syntax error names its line", this::syntaxError);
        check("Static timing analysis and resource count", this::analysis);
        check("A released tri-state bus reads Z", this::triState);
        check("Flip-flop clock-to-Q delay starts a timing path", this::flipFlopTiming);
        check("Fault coverage finds an untested fault", this::faultCoverage);
        check("Macros become modules on the schematic", this::modules);
        check("Every example simulates and draws a schematic", this::examples);
        check("Every netlist in the guide is valid", this::guideNetlists);
        check("Engine speed is at least 10,000 events per second", this::speed);
        return results;
    }

    private void check(String name, Check check) {
        try {
            check.run();
            results.add(new Result(name, true, ""));
        } catch (Exception | AssertionError e) {
            results.add(new Result(name, false, String.valueOf(e.getMessage())));
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void expectEqual(String expected, String actual, String what) {
        expect(expected.equals(actual), what + ": expected " + expected + " but got " + actual);
    }

    private String outputColumn(String type, String wires) {
        String netlist = "WIRE A\nWIRE B\nWIRE Y\n" + type + " g 1 " + wires + " Y\n";
        List<String> inputs = wires.contains("B") ? List.of("A", "B") : List.of("A");
        StringBuilder column = new StringBuilder();
        for (TruthTable.Row row : service.truthTable(netlist, inputs, List.of("Y")).getRows()) {
            column.append(row.getOutputStates().get(0));
        }
        return column.toString();
    }

    private void gates() {
        expectEqual("0001", outputColumn("AND", "A B"), "AND");
        expectEqual("0111", outputColumn("OR", "A B"), "OR");
        expectEqual("10", outputColumn("NOT", "A"), "NOT");
        expectEqual("1110", outputColumn("NAND", "A B"), "NAND");
        expectEqual("1000", outputColumn("NOR", "A B"), "NOR");
        expectEqual("0110", outputColumn("XOR", "A B"), "XOR");
        expectEqual("1001", outputColumn("XNOR", "A B"), "XNOR");
    }

    private static String stateAfter(String netlist, long simTime, String... wires) {
        SimulationContext context = new SimulationContext();
        NetlistParser parser = new NetlistParser(context);
        parser.parseString("SIM_TIME " + simTime + "\n" + netlist);
        parser.getComponents().forEach(component -> {
            if (component instanceof simulator.components.Clock) {
                ((simulator.components.Clock) component).generateClockPulses();
            }
        });
        context.runSimulation();
        StringBuilder values = new StringBuilder();
        for (String wire : wires) {
            values.append(parser.getWire(wire).getCurrentState().name().charAt(0));
        }
        return values.toString();
    }

    private void timing() {
        String netlist = "WIRE A\nWIRE Y\nNOT n 7 A Y\nSTIMULUS 0 A 1\n";
        expectEqual("U", stateAfter(netlist, 6, "Y"), "Output before the delay");
        expectEqual("L", stateAfter(netlist, 7, "Y"), "Output after the delay");
    }

    private void waveform() throws Exception {
        File file = File.createTempFile("simulator-check", ".vcd");
        try {
            service.simulate("WIRE A\nWIRE Y\nNOT n 5 A Y\nSTIMULUS 0 A 1\n", file.getPath());
            String vcd = Files.readString(file.toPath());
            expect(vcd.contains("$var wire 1") && vcd.contains("$dumpvars") && vcd.contains("#5"),
                    "The VCD file is missing expected sections");
        } finally {
            file.delete();
        }
    }

    private void fullAdderTable() {
        CircuitPorts ports = service.detectPorts(ExampleCircuits.defaultNetlist());
        TruthTable table = service.truthTable(ExampleCircuits.defaultNetlist(), ports.getInputs(), ports.getOutputs());
        expect(table.getRows().size() == 8, "Expected 8 rows");
        for (TruthTable.Row row : table.getRows()) {
            int total = 0;
            for (String input : row.getInputStates()) {
                total += Integer.parseInt(input);
            }
            expectEqual(String.valueOf(total & 1), row.getOutputStates().get(0), "Sum");
            expectEqual(String.valueOf(total >> 1), row.getOutputStates().get(1), "Carry");
        }
    }

    private void functionalEquivalence() {
        List<String> inputs = List.of("Input_A", "Input_B", "Input_Cin");
        expect(service.equivalent(ExampleCircuits.defaultNetlist(), ExampleCircuits.netlist("Full adder (functional)"),
                inputs, List.of("Sum", "CarryOut")).isEquivalent(), "The two adders should be equivalent");
    }

    private void findsDifference() {
        var result = service.equivalent("WIRE A\nWIRE B\nWIRE Y\nAND g 1 A B Y\n",
                "WIRE A\nWIRE B\nWIRE Y\nOR g 1 A B Y\n", List.of("A", "B"), List.of("Y"));
        expect(!result.isEquivalent() && result.getCounterexampleRow() == 1, "Expected a difference at row 1");
    }

    private void counter() {
        String netlist = "WIRE CLK\nWIRE D0\nWIRE Q0\nWIRE D1\nWIRE Q1\nCLOCK c 20 CLK\n"
                + "DFF f0 2 D0 CLK Q0\nNOT n0 1 Q0 D0\nDFF f1 2 D1 CLK Q1\nXOR x1 1 Q0 Q1 D1\n";
        expectEqual("LH", stateAfter(netlist, 15, "Q1", "Q0"), "Count after one edge");
        expectEqual("HL", stateAfter(netlist, 35, "Q1", "Q0"), "Count after two edges");
        expectEqual("HH", stateAfter(netlist, 55, "Q1", "Q0"), "Count after three edges");
        expectEqual("LL", stateAfter(netlist, 75, "Q1", "Q0"), "Count after four edges");
    }

    private void stateTable() {
        String netlist = ExampleCircuits.netlist("Up-down counter (Mealy/Moore)");
        CircuitPorts ports = service.detectPorts(netlist);
        TruthTable table = service.truthTable(netlist, ports.getInputs(), ports.getOutputs());
        expectEqual("[UP, DOWN, S0, S1]", table.getInputs().toString(), "Columns");
        expect(table.getRows().size() == 16, "Expected 16 rows");
        for (TruthTable.Row row : table.getRows()) {
            int up = Integer.parseInt(row.getInputStates().get(0));
            int down = Integer.parseInt(row.getInputStates().get(1));
            int state = Integer.parseInt(row.getInputStates().get(2)) + 2 * Integer.parseInt(row.getInputStates().get(3));
            int next = up == down ? state : up == 1 ? (state + 1) % 4 : (state + 3) % 4;
            expectEqual(String.valueOf(next & 1), row.getOutputStates().get(0), "Next S0");
            expectEqual(String.valueOf(next >> 1), row.getOutputStates().get(1), "Next S1");
        }
    }

    private void macros() {
        String netlist = ExampleCircuits.netlist("Hierarchical adder (macros)").replace("SIM_TIME 200\n", "");
        expectEqual("LLH", stateAfter(netlist, 200, "S1", "S0", "Carry1"), "01 + 11 should give 100 as Carry1 S1 S0");
        expectEqual("HH", stateAfter("WIRE A\nWIRE B\nWIRE S\nWIRE C\nFULL_ADDER u A B B S C\n"
                + "STIMULUS 0 A 1\nSTIMULUS 0 B 1\n", 100, "S", "C"), "Library adder");
    }

    private void fault() {
        expectEqual("L", stateAfter("WIRE A\nWIRE Y\nFAULT STUCK_AT_1 A\nNOT n 1 A Y\nSTIMULUS 0 A 0\n", 10, "Y"),
                "Output with its input stuck at 1");
    }

    private void assertion() {
        SimulationRunner.SimulationResult result = service.simulate(
                "WIRE A\nWIRE Y\nNOT n 1 A Y\nSTIMULUS 0 A 1\nASSERT 5 Y 0\nASSERT 6 Y 1\n", null);
        expect(result.getFailedAssertionCount() == 1, "Expected one of two assertions to fail");
        expectEqual("FAIL t=6 Y expected 1, was 0", result.getAssertions().get(1).describe(), "Assertion report");
    }

    private void loop() {
        expectFailure("WIRE A\nWIRE B\nNOT n1 1 A B\nNOT n2 1 B A\n", "loop");
    }

    private void driverConflict() {
        expectFailure("WIRE A\nWIRE B\nWIRE Y\nAND g1 1 A B Y\nOR g2 1 A B Y\n", "more than one");
    }

    private void syntaxError() {
        expectFailure("WIRE A\nWIRE B\nFOO x 1 A B\n", "line 3");
    }

    private void expectFailure(String netlist, String message) {
        try {
            service.simulate(netlist, null);
        } catch (RuntimeException e) {
            expect(e.getMessage().contains(message), "Unexpected message: " + e.getMessage());
            return;
        }
        throw new IllegalStateException("The netlist should have been rejected");
    }

    private void analysis() {
        TimingAnalysisResult timing = service.analyze(ExampleCircuits.defaultNetlist());
        expect(timing.getCriticalPathDelay() == 30, "Expected a 30 ns critical path");
        expect(!timing.getCriticalPath().isEmpty(), "Expected the critical path's gates to be named");
        expectEqual("5 components (2 AND, 1 OR, 2 XOR), 8 wires",
                service.resources(ExampleCircuits.defaultNetlist()).describe(), "Resources");
    }

    private void triState() {
        SimulationRunner.SimulationResult result = service.simulate(
                "SIM_TIME 50\nWIRE D\nWIRE E\nWIRE BUS\nBUFFER b 1 D E BUS\n"
                        + "STIMULUS 0 D 1\nSTIMULUS 0 E 1\nSTIMULUS 20 E 0\nASSERT 10 BUS 1\nASSERT 30 BUS Z\n", null);
        expect(result.getFailedAssertionCount() == 0, "Expected the bus to be Z after its buffer lets go");
    }

    private void flipFlopTiming() {
        TimingAnalysisResult timing = service.analyze("WIRE D\nWIRE C\nWIRE Q\nWIRE Y\nDFF f 4 D C Q\nNOT n 6 Q Y\n");
        expect(timing.getCriticalPathDelay() == 10, "Expected 4 ns clock-to-Q plus a 6 ns gate");
        expectEqual("f > n", String.join(" > ", timing.getCriticalPath()), "Critical path");
    }

    private void faultCoverage() {
        FaultCoverageAnalyzer.Result result = service.faultCoverage(
                "SIM_TIME 50\nWIRE A\nWIRE Y\nNOT n 1 A Y\nSTIMULUS 0 A 0\n");
        expect(result.getTotalFaults() == 4, "Expected two faults on each of two wires");
        expect(result.getUndetected().contains("A stuck at 0"), "A stuck at 0 cannot be seen when A is always 0");
        expect(!result.getUndetected().contains("A stuck at 1"), "A stuck at 1 changes the output");
    }

    private void modules() {
        NetlistParser parser = new NetlistParser(new SimulationContext());
        parser.parseString("WIRE A\nWIRE B\nWIRE S\nWIRE C\nHALF_ADDER h A B S C\n");
        expect(parser.getModules().size() == 1, "Expected one module");
        SchematicLayout layout = SchematicLayout.build(parser, true);
        expect(layout.getNodes().stream().anyMatch(node -> node.kind == SchematicLayout.Kind.MODULE
                && node.id.equals("h") && node.outputCount == 2), "Expected a module with two outputs");
    }

    private void examples() {
        for (String name : ExampleCircuits.names()) {
            String netlist = ExampleCircuits.netlist(name);
            SimulationRunner.SimulationResult result = service.simulate(netlist, null);
            expect(result.getEventsProcessed() > 0, name + " has no signal activity");
            NetlistParser parser = new NetlistParser(new SimulationContext());
            parser.parseString(netlist);
            expect(!SchematicLayout.build(parser).getNets().isEmpty(), name + " has no schematic");
        }
    }

    private void guideNetlists() {
        for (String netlist : guideNetlistSamples()) {
            service.simulate(netlist, null);
        }
    }

    public static List<String> guideNetlistSamples() {
        List<String> samples = new ArrayList<>();
        UserGuide.write(new UserGuide.Sink() {
            @Override public void contents(String[][] sections) { }
            @Override public void section(String id, String title) { }
            @Override public void heading(String title) { }
            @Override public void paragraph(String text) { }
            @Override public void bullets(String... items) { }
            @Override public void steps(String... items) { }
            @Override public void netlist(String text) { samples.add(text); }
            @Override public void code(String text) { }
            @Override public void table(String[] headers, String[]... rows) { }
        }, UserGuide.Surface.COMMAND_LINE);
        return samples;
    }

    private void speed() {
        SimulationRunner.SimulationResult result = service.simulate(
                "SIM_TIME 400000\nWIRE C\nWIRE Y\nCLOCK c 2 C\nNOT n 1 C Y\n", null);
        expect(result.getEventsProcessed() >= 400_000, "Only " + result.getEventsProcessed() + " events ran");
        long millis = Math.max(1, result.getComputeTimeMillis());
        long perSecond = result.getEventsProcessed() * 1000 / millis;
        expect(perSecond >= REQUIRED_EVENTS_PER_SECOND, "Measured " + perSecond + " events per second");
    }
}
