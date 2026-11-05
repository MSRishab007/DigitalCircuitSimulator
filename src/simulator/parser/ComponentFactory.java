package simulator.parser;

import simulator.components.AndGate;
import simulator.components.NotGate;
import simulator.components.OrGate;
import simulator.components.SequentialComponent;
import simulator.components.TriStateBuffer;
import simulator.components.XorGate;
import simulator.components.XnorGate;
import simulator.components.LowState;
import simulator.components.NandGate;
import simulator.components.NorGate;
import simulator.components.Clock;
import simulator.core.DigitalComponent;
import simulator.engine.SimulationContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class ComponentFactory {
    private static final Map<String, ComponentCreator> CREATORS = new LinkedHashMap<>();

    static {
        register("AND", AndGate::new);
        register("OR", OrGate::new);
        register("NOT", NotGate::new);
        register("DFF", (id, delay) -> new SequentialComponent(id, delay, new LowState()));
        register("CLOCK", Clock::new);
        register("BUFFER", TriStateBuffer::new);
        register("XOR", XorGate::new);
        register("NAND", NandGate::new);
        register("NOR", NorGate::new);
        register("XNOR", XnorGate::new);
    }
    
    public static DigitalComponent createComponent(String type, String id, int param) {
        return createComponent(type, id, param, SimulationContext.defaultContext());
    }

    public static DigitalComponent createComponent(String type, String id, int param,
                                                    SimulationContext simulationContext) {
        if (simulationContext == null) {
            throw new IllegalArgumentException("Simulation context cannot be null");
        }
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Component type cannot be blank");
        }
        ComponentCreator creator = CREATORS.get(type.toUpperCase(Locale.ROOT));
        if (creator == null) {
            throw new IllegalArgumentException("Unsupported component type: " + type);
        }
        DigitalComponent component = creator.create(id, param);
        component.setSimulationContext(simulationContext);
        return component;
    }

    public static void register(String type, ComponentCreator creator) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Component type cannot be blank");
        }
        if (creator == null) {
            throw new IllegalArgumentException("Component creator cannot be null");
        }
        CREATORS.put(type.toUpperCase(Locale.ROOT), creator);
    }

    public static Map<String, ComponentCreator> registeredComponents() {
        return Collections.unmodifiableMap(CREATORS);
    }
}