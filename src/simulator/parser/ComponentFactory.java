package simulator.parser;

import simulator.components.AndGate;
import simulator.components.NotGate;
import simulator.components.OrGate;
import simulator.components.SequentialComponent;
import simulator.components.TriStateBuffer;
import simulator.components.XorGate;
import simulator.components.LowState;
import simulator.components.NandGate;
import simulator.components.NorGate;
import simulator.components.Clock;
import simulator.core.DigitalComponent;

public class ComponentFactory {
    
    public static DigitalComponent createComponent(String type, String id, int param) {
        switch (type.toUpperCase()) {
            case "AND":
                return new AndGate(id, param);
            case "OR":
                return new OrGate(id, param);
            case "NOT":
                return new NotGate(id, param);
            case "DFF":
                return new SequentialComponent(id, param, new LowState());
            case "CLOCK":
                // For clocks, 'param' represents the full time period in ns
                return new Clock(id, param);
            case "BUFFER": 
                return new TriStateBuffer(id, param);
            case "XOR": 
                return new XorGate(id, param);
            case "NAND": 
                return new NandGate(id, param);
            case "NOR": 
                return new NorGate(id, param);
            default:
                throw new IllegalArgumentException("Unsupported component type: " + type);
        }
    }
}