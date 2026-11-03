package simulator.parser;

import simulator.core.DigitalComponent;

@FunctionalInterface
public interface ComponentCreator {
    DigitalComponent create(String id, int parameter);
}
