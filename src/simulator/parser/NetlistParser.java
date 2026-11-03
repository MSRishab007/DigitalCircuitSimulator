package simulator.parser;

import simulator.core.DigitalComponent;
import simulator.core.Wire;
import simulator.engine.EventEngine;
import simulator.fault.FaultInjector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class NetlistParser {
    private final Map<String, Wire> wires = new HashMap<>();
    private final List<DigitalComponent> components = new ArrayList<>();
    private final Map<String, MacroDef> macros = new HashMap<>();

    // Internal class to hold Macro Blueprints
    private static class MacroDef {
        String name;
        List<String> ports = new ArrayList<>();
        List<String> bodyLines = new ArrayList<>();
    }

    public void parse(String filepath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
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
                        currentMacro = new MacroDef();
                        currentMacro.name = tokens[1].toUpperCase();
                        // Ports start at index 2
                        currentMacro.ports.addAll(Arrays.asList(tokens).subList(2, tokens.length));
                        continue;
                    } else if (command.equals("ENDDEF")) {
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
                        EventEngine.getInstance().setMaxSimulationTime(Long.parseLong(tokens[1]));
                        EventEngine.getInstance().resetEngine();
                    } 
                    else if (command.equals("WIRE")) {
                        wires.put(tokens[1], new Wire(tokens[1]));
                    } 
                    else if (command.equals("FAULT")) {
                        String type = tokens[1].toUpperCase();
                        Wire target = getWire(tokens[2]);
                        if (type.equals("STUCK_AT_0")) FaultInjector.stuckAtZero(target);
                        else if (type.equals("STUCK_AT_1")) FaultInjector.stuckAtOne(target);
                    }
                    else if (command.equals("STIMULUS")) {
                        long time = Long.parseLong(tokens[1]);
                        Wire target = getWire(tokens[2]);
                        simulator.core.VoltageState state = tokens[3].equals("1") ? 
                            simulator.core.VoltageState.HIGH : 
                            (tokens[3].equals("0") ? simulator.core.VoltageState.LOW : simulator.core.VoltageState.HIGH_Z);
                        EventEngine.getInstance().scheduleEvent(new simulator.engine.SignalEvent(time, target, state));
                    }
                    else if (macros.containsKey(command)) {
                        // Expand Macro
                        instantiateMacro(macros.get(command), tokens);
                    } 
                    else {
                        // Primitive Gate
                        instantiateGate(tokens, "", new HashMap<>());
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Syntax error at line " + lineNumber + ": " + line, e);
                }
            }
        }
    }

    private void instantiateMacro(MacroDef macro, String[] args) {
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
                wires.put(localWire, new Wire(localWire));
                wireMap.put(tokens[1], localWire);
            } else {
                instantiateGate(tokens, instanceId + "_", wireMap);
            }
        }
    }

    private void instantiateGate(String[] tokens, String idPrefix, Map<String, String> wireMap) {
        String type = tokens[0];
        String id = idPrefix + tokens[1];
        int param = Integer.parseInt(tokens[2]);

        DigitalComponent component = ComponentFactory.createComponent(type, id, param);
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
}