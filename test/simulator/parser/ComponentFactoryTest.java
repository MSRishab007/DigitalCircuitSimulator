package simulator.parser;

import org.junit.jupiter.api.Test;
import simulator.components.AndGate;
import simulator.components.Clock;
import simulator.components.NandGate;
import simulator.components.NorGate;
import simulator.components.NotGate;
import simulator.components.OrGate;
import simulator.components.SequentialComponent;
import simulator.components.TriStateBuffer;
import simulator.components.XorGate;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentFactoryTest {
    @Test
    void createsEverySupportedComponentType() {
        assertInstanceOf(AndGate.class, ComponentFactory.createComponent("AND", "a", 1));
        assertInstanceOf(OrGate.class, ComponentFactory.createComponent("OR", "o", 1));
        assertInstanceOf(NotGate.class, ComponentFactory.createComponent("NOT", "n", 1));
        assertInstanceOf(SequentialComponent.class, ComponentFactory.createComponent("DFF", "d", 1));
        assertInstanceOf(Clock.class, ComponentFactory.createComponent("CLOCK", "c", 2));
        assertInstanceOf(TriStateBuffer.class, ComponentFactory.createComponent("BUFFER", "b", 1));
        assertInstanceOf(XorGate.class, ComponentFactory.createComponent("XOR", "x", 1));
        assertInstanceOf(NandGate.class, ComponentFactory.createComponent("NAND", "na", 1));
        assertInstanceOf(NorGate.class, ComponentFactory.createComponent("NOR", "no", 1));
    }

    @Test
    void rejectsUnsupportedComponentType() {
        assertThrows(IllegalArgumentException.class,
                () -> ComponentFactory.createComponent("UNKNOWN", "id", 1));
    }

    @Test
    void exposesRegisteredCreatorsAndSupportsExtension() {
        assertTrue(ComponentFactory.registeredComponents().containsKey("AND"));
        ComponentFactory.register("TEST", (id, delay) -> new AndGate(id, delay));

        assertInstanceOf(AndGate.class, ComponentFactory.createComponent("test", "custom", 2));
    }
}
