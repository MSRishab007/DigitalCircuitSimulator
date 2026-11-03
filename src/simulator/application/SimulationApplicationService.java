package simulator.application;

import simulator.engine.CircuitEquivalenceChecker;
import simulator.engine.StaticTimingAnalyzer;
import simulator.engine.TimingAnalysisResult;
import simulator.engine.TruthTable;
import simulator.engine.TruthTableGenerator;
import simulator.parser.NetlistParser;
import simulator.parser.SimulationRunner;
import simulator.parser.TopologyValidator;
import simulator.engine.SimulationContext;

import java.util.List;

/**
 * Application boundary shared by CLI, GUI, and WebAssembly clients.
 */
public final class SimulationApplicationService {
    public SimulationRunner.SimulationResult simulate(String netlist, String waveformPath) {
        return new SimulationRunner().runString(netlist, waveformPath);
    }

    public ValidationResult validate(String netlist) {
        NetlistParser parser = parse(netlist);
        boolean valid = new TopologyValidator().validateNoCombinationalLoops(
                parser.getComponents());
        return new ValidationResult(valid,
                valid ? "Netlist is valid" : "Unclocked combinational loop detected");
    }

    public TimingAnalysisResult analyze(String netlist) {
        NetlistParser parser = parse(netlist);
        ValidationResult validation = validate(netlist);
        if (!validation.isValid()) {
            throw new IllegalStateException(validation.getMessage());
        }
        return new StaticTimingAnalyzer().analyze(parser.getComponents());
    }

    public TruthTable truthTable(String netlist, List<String> inputs,
                                 List<String> outputs) {
        return new TruthTableGenerator().generate(netlist, inputs, outputs);
    }

    public CircuitEquivalenceChecker.EquivalenceResult equivalent(
            String firstNetlist, String secondNetlist, List<String> inputs,
            List<String> outputs) {
        return new CircuitEquivalenceChecker().compare(
                firstNetlist, secondNetlist, inputs, outputs);
    }

    private static NetlistParser parse(String netlist) {
        if (netlist == null || netlist.isBlank()) {
            throw new IllegalArgumentException("Netlist cannot be blank");
        }
        NetlistParser parser = new NetlistParser(new SimulationContext());
        parser.parseString(netlist);
        return parser;
    }

    public static final class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}
