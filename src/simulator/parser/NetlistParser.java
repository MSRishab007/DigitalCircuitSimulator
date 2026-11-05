package simulator.parser;

import simulator.components.BooleanExpression;
import simulator.components.FunctionalBlock;
import simulator.components.Module;
import simulator.core.DigitalComponent;
import simulator.core.Wire;
import simulator.engine.SimulationContext;
import simulator.engine.SimulationAssertion;
import simulator.fault.FaultInjector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class NetlistParser {
    private static final int MAX_MACRO_DEPTH = 32;

    private final SimulationContext simulationContext;
    private final Map<String, Wire> wires = new LinkedHashMap<>();
    private final List<DigitalComponent> components = new ArrayList<>();
    private final Map<String, MacroDef> macros = new HashMap<>();
    private final List<Module> modules = new ArrayList<>();

    public NetlistParser() {
        this(SimulationContext.defaultContext());
    }

    public NetlistParser(SimulationContext simulationContext) {
        if (simulationContext == null) {
            throw new IllegalArgumentException("Simulation context cannot be null");
        }
        this.simulationContext = simulationContext;
    }

    private static class MacroDef {
        String name;
        List<String> ports = new ArrayList<>();
        List<String> bodyLines = new ArrayList<>();
    }

    public void parse(String filepath) throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
        parseReader(reader);
    }
}

public void parseString(String content) {
    try (BufferedReader reader = new BufferedReader(new java.io.StringReader(content))) {
        parseReader(reader);
    } catch (IOException e) {
        throw new RuntimeException("Error reading string content", e);
    }
}

private void parseReader(BufferedReader reader) throws IOException {
    wires.clear();
    components.clear();
    modules.clear();
    macros.clear();
    simulationContext.reset();
    loadStandardLibrary();
    
    String line;
    int lineNumber = 0;
    MacroDef currentMacro = null;
    List<Runnable> faults = new ArrayList<>();
    
    while ((line = reader.readLine()) != null) {
        lineNumber++;
        int comment = line.indexOf('#');
        line = (comment >= 0 ? line.substring(0, comment) : line).trim();
        
        if (line.isEmpty()) continue;

        String[] tokens = line.split("\\s+");
        String command = tokens[0].toUpperCase();

        try {
            if (command.equals("DEF")) {
                requireLength(tokens, 3, lineNumber, "DEF <name> <ports...>");
                if (currentMacro != null) {
                    throw new IllegalArgumentException("Nested macro definitions are not supported");
                }
                currentMacro = new MacroDef();
                currentMacro.name = tokens[1].toUpperCase();
                currentMacro.ports.addAll(Arrays.asList(tokens).subList(2, tokens.length));
                continue;
            } else if (command.equals("ENDDEF")) {
                if (currentMacro == null) {
                    throw new IllegalArgumentException("ENDDEF without DEF");
                }
                macros.put(currentMacro.name, currentMacro);
                currentMacro = null;
                continue;
            }

            if (currentMacro != null) {
                currentMacro.bodyLines.add(line);
                continue;
            }

            if (command.equals("SIM_TIME")) {
                requireLength(tokens, 2, lineNumber, "SIM_TIME <nanoseconds>");
                simulationContext.getEventEngine().setMaxSimulationTime(Long.parseLong(tokens[1]));
            } 
            else if (command.equals("ASSERT")) {
                requireLength(tokens, 4, lineNumber, "ASSERT <time> <wire> <0|1|X|Z>");
                long assertionTime = Long.parseLong(tokens[1]);
                Wire target = getWire(tokens[2]);
                simulationContext.addAssertion(new SimulationAssertion(
                        assertionTime, target, parseState(tokens[3], "Assertion")));
            }
            else if (command.equals("WIRE")) {
                requireLength(tokens, 2, lineNumber, "WIRE <name>");
                if (wires.containsKey(tokens[1])) {
                    throw new IllegalArgumentException("Duplicate wire: " + tokens[1]);
                }
                wires.put(tokens[1], new Wire(tokens[1]));
            } 
            else if (command.equals("FAULT")) {
                requireLength(tokens, 3, lineNumber, "FAULT <STUCK_AT_0|STUCK_AT_1> <wire>");
                String type = tokens[1].toUpperCase();
                Wire target = getWire(tokens[2]);
                if (type.equals("STUCK_AT_0")) faults.add(() -> FaultInjector.stuckAtZero(target));
                else if (type.equals("STUCK_AT_1")) faults.add(() -> FaultInjector.stuckAtOne(target));
                else throw new IllegalArgumentException("Unsupported fault type: " + type);
            }
            else if (command.equals("STIMULUS")) {
                requireLength(tokens, 4, lineNumber, "STIMULUS <time> <wire> <0|1|Z>");
                long time = Long.parseLong(tokens[1]);
                if (time < 0) throw new IllegalArgumentException("Stimulus time cannot be negative");
                Wire target = getWire(tokens[2]);
                simulator.core.VoltageState state = parseStimulusState(tokens[3]);
                simulationContext.getEventEngine().scheduleEvent(new simulator.engine.SignalEvent(time, target, state));
            }
            else if (macros.containsKey(command)) {
                instantiateMacro(macros.get(command), tokens, "", new HashMap<>(), 0, null);
            } 
            else {
                if (tokens.length < 2) {
                    throw new IllegalArgumentException("Unknown command: " + command);
                }
                instantiateGate(tokens, "", new HashMap<>(), null);
            }
        } catch (Exception e) {
            throw new RuntimeException("Syntax error at line " + lineNumber + ": " + line
                    + " (" + e.getMessage() + ")", e);
        }
    }
    if (currentMacro != null) {
        throw new IllegalArgumentException("Missing ENDDEF for macro: " + currentMacro.name);
    }
    faults.forEach(Runnable::run);
    for (DigitalComponent component : components) {
        if (component instanceof simulator.components.SequentialComponent
                || component instanceof simulator.components.FunctionalBlock) {
            component.evaluate();
        }
    }
}
    private void loadStandardLibrary() {
        MacroDef current = null;
        for (String line : StandardLibrary.SOURCE.split("\n")) {
            String[] tokens = line.split("\\s+");
            if (tokens[0].equals("DEF")) {
                current = new MacroDef();
                current.name = tokens[1];
                current.ports.addAll(Arrays.asList(tokens).subList(2, tokens.length));
            } else if (tokens[0].equals("ENDDEF")) {
                macros.put(current.name, current);
            } else {
                current.bodyLines.add(line);
            }
        }
    }

    private void instantiateMacro(MacroDef macro, String[] args, String idPrefix,
                                  Map<String, String> outerWires, int depth, Module parent) {
        if (depth > MAX_MACRO_DEPTH) {
            throw new IllegalArgumentException("Macro " + macro.name + " uses itself");
        }
        if (args.length != macro.ports.size() + 2) {
            throw new IllegalArgumentException("Macro " + macro.name + " expects " +
                    macro.ports.size() + " ports");
        }
        String instanceId = idPrefix + args[1];
        Module module = new Module(instanceId, 0, 0);
        module.setTypeName(macro.name);
        module.setSimulationContext(simulationContext);
        if (parent == null) {
            modules.add(module);
        } else {
            parent.addSubComponent(module);
        }
        Map<String, String> wireMap = new HashMap<>();

        for (int i = 0; i < macro.ports.size(); i++) {
            wireMap.put(macro.ports.get(i), outerWires.getOrDefault(args[i + 2], args[i + 2]));
        }

        for (String line : macro.bodyLines) {
            String[] tokens = line.split("\\s+");
            String command = tokens[0].toUpperCase();
            if (command.equals("WIRE")) {
                String localWire = instanceId + "_" + tokens[1];
                if (wires.containsKey(localWire)) {
                    throw new IllegalArgumentException("Duplicate macro wire: " + localWire);
                }
                wires.put(localWire, new Wire(localWire));
                wireMap.put(tokens[1], localWire);
            } else if (macros.containsKey(command)) {
                instantiateMacro(macros.get(command), tokens, instanceId + "_", wireMap, depth + 1, module);
            } else {
                instantiateGate(tokens, instanceId + "_", wireMap, module);
            }
        }
    }

    private void instantiateGate(String[] tokens, String idPrefix, Map<String, String> wireMap, Module owner) {
        if (tokens[0].equalsIgnoreCase("FUNC")) {
            instantiateFunction(tokens, idPrefix, wireMap, owner);
            return;
        }
        if (tokens.length < 4) {
            throw new IllegalArgumentException("Gate syntax: TYPE ID DELAY [INPUTS...] OUTPUT");
        }
        String type = tokens[0];
        String id = idPrefix + tokens[1];
        int param = Integer.parseInt(tokens[2]);
        if (param < 0) {
            throw new IllegalArgumentException("Propagation delay cannot be negative");
        }

        DigitalComponent component = ComponentFactory.createComponent(type, id, param, simulationContext);
        int inputCount = tokens.length - 4;
        if (inputCount != component.getInputPins().size()) {
            throw new IllegalArgumentException(type + " expects " + component.getInputPins().size() +
                    " inputs but received " + inputCount);
        }
        components.add(component);
        if (owner != null) {
            owner.addSubComponent(component);
        }

        String rawOut = tokens[tokens.length - 1];
        component.getOutputPin(0).connect(getWire(wireMap.getOrDefault(rawOut, rawOut)));

        for (int i = 3; i < tokens.length - 1; i++) {
            String rawIn = tokens[i];
            getWire(wireMap.getOrDefault(rawIn, rawIn)).addObserver(component.getInputPin(i - 3));
        }
    }

    private void instantiateFunction(String[] tokens, String idPrefix, Map<String, String> wireMap, Module owner) {
        int equals = Arrays.asList(tokens).indexOf("=");
        if (equals < 4 || equals == tokens.length - 1) {
            throw new IllegalArgumentException("Function syntax: FUNC ID DELAY [INPUTS...] OUTPUT = EXPRESSION");
        }
        int delay = Integer.parseInt(tokens[2]);
        if (delay < 0) {
            throw new IllegalArgumentException("Propagation delay cannot be negative");
        }
        List<String> inputNames = Arrays.asList(tokens).subList(3, equals - 1);
        String expressionText = String.join(" ", Arrays.asList(tokens).subList(equals + 1, tokens.length));
        BooleanExpression expression = BooleanExpression.parse(expressionText, inputNames);

        FunctionalBlock block = new FunctionalBlock(idPrefix + tokens[1], delay, inputNames.size(), expression);
        block.setSimulationContext(simulationContext);
        components.add(block);
        if (owner != null) {
            owner.addSubComponent(block);
        }

        String rawOut = tokens[equals - 1];
        block.getOutputPin(0).connect(getWire(wireMap.getOrDefault(rawOut, rawOut)));
        for (int i = 0; i < inputNames.size(); i++) {
            String rawIn = inputNames.get(i);
            getWire(wireMap.getOrDefault(rawIn, rawIn)).addObserver(block.getInputPin(i));
        }
    }

    public Wire getWire(String name) {
        if (!wires.containsKey(name)) throw new IllegalArgumentException("Wire not defined: " + name);
        return wires.get(name);
    }
    public List<Wire> getAllWires() { return new ArrayList<>(wires.values()); }
    public List<DigitalComponent> getComponents() { return components; }
    public List<Module> getModules() { return modules; }
    public SimulationContext getSimulationContext() { return simulationContext; }
    private static simulator.core.VoltageState parseState(String token, String kind) {
        if (token.equals("1")) return simulator.core.VoltageState.HIGH;
        if (token.equals("0")) return simulator.core.VoltageState.LOW;
        if (token.equalsIgnoreCase("Z")) return simulator.core.VoltageState.HIGH_Z;
        if (token.equalsIgnoreCase("X")) return simulator.core.VoltageState.UNKNOWN;
        throw new IllegalArgumentException(kind + " must be 0, 1, X, or Z");
    }
    private static simulator.core.VoltageState parseStimulusState(String token) {
        if (token.equals("1")) return simulator.core.VoltageState.HIGH;
        if (token.equals("0")) return simulator.core.VoltageState.LOW;
        if (token.equalsIgnoreCase("Z")) return simulator.core.VoltageState.HIGH_Z;
        throw new IllegalArgumentException("Stimulus must be 0, 1, or Z");
    }

    private static void requireLength(String[] tokens, int length, int lineNumber, String syntax) {
        if (tokens.length < length) {
            throw new IllegalArgumentException("Line " + lineNumber + " requires: " + syntax);
        }
    }
}