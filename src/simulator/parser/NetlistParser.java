package simulator.parser;

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
    private final SimulationContext simulationContext;
    private final Map<String, Wire> wires = new HashMap<>();
    private final List<DigitalComponent> components = new ArrayList<>();
    private final Map<String, MacroDef> macros = new HashMap<>();

    public NetlistParser() {
        this(SimulationContext.defaultContext());
    }

    public NetlistParser(SimulationContext simulationContext) {
        if (simulationContext == null) {
            throw new IllegalArgumentException("Simulation context cannot be null");
        }
        this.simulationContext = simulationContext;
    }

    // Internal class to hold Macro Blueprints
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
    macros.clear();
    simulationContext.reset();
    
    String line;
    int lineNumber = 0;
    MacroDef currentMacro = null;
    
    while ((line = reader.readLine()) != null) {
        lineNumber++;
        line = line.trim();
        
        if (line.isEmpty() || line.startsWith("#")) continue;

        String[] tokens = line.split("\\s+");
        String command = tokens[0].toUpperCase();

        try {
            // --- MACRO RECORDING MODE ---
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

            // --- STANDARD EXECUTION MODE ---
            if (command.equals("SIM_TIME")) {
                requireLength(tokens, 2, lineNumber, "SIM_TIME <nanoseconds>");
                simulationContext.reset();
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
                if (type.equals("STUCK_AT_0")) FaultInjector.stuckAtZero(target);
                else if (type.equals("STUCK_AT_1")) FaultInjector.stuckAtOne(target);
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
                instantiateMacro(macros.get(command), tokens);
            } 
            else {
                if (tokens.length < 2) {
                    throw new IllegalArgumentException("Unknown command: " + command);
                }
                instantiateGate(tokens, "", new HashMap<>());
            }
        } catch (Exception e) {
            throw new RuntimeException("Syntax error at line " + lineNumber + ": " + line, e);
        }
    }
    if (currentMacro != null) {
        throw new IllegalArgumentException("Missing ENDDEF for macro: " + currentMacro.name);
    }
}
    private void instantiateMacro(MacroDef macro, String[] args) {
        if (args.length != macro.ports.size() + 2) {
            throw new IllegalArgumentException("Macro " + macro.name + " expects " +
                    macro.ports.size() + " ports");
        }
        String instanceId = args[1]; // e.g., ha1
        Map<String, String> wireMap = new HashMap<>();
        
        // Map macro variables to the actual main wires provided
        for (int i = 0; i < macro.ports.size(); i++) {
            wireMap.put(macro.ports.get(i), args[i + 2]);
        }

        // Play back the macro lines
        for (String line : macro.bodyLines) {
            String[] tokens = line.split("\\s+");
            if (tokens[0].toUpperCase().equals("WIRE")) {
                // Create encapsulated local wires (e.g., ha1_Internal_OR)
                String localWire = instanceId + "_" + tokens[1];
                if (wires.containsKey(localWire)) {
                    throw new IllegalArgumentException("Duplicate macro wire: " + localWire);
                }
                wires.put(localWire, new Wire(localWire));
                wireMap.put(tokens[1], localWire);
            } else {
                instantiateGate(tokens, instanceId + "_", wireMap);
            }
        }
    }

    private void instantiateGate(String[] tokens, String idPrefix, Map<String, String> wireMap) {
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

        String rawOut = tokens[tokens.length - 1];
        component.getOutputPin(0).connect(getWire(wireMap.getOrDefault(rawOut, rawOut)));

        for (int i = 3; i < tokens.length - 1; i++) {
            String rawIn = tokens[i];
            getWire(wireMap.getOrDefault(rawIn, rawIn)).addObserver(component.getInputPin(i - 3));
        }
    }

    public Wire getWire(String name) {
        if (!wires.containsKey(name)) throw new IllegalArgumentException("Wire not defined: " + name);
        return wires.get(name);
    }
    public List<Wire> getAllWires() { return new ArrayList<>(wires.values()); }
    public List<DigitalComponent> getComponents() { return components; }
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