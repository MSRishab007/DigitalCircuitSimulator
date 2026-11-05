package simulator.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runtime dependencies for one simulation session.
 */
public final class SimulationContext {
    private static final SimulationContext DEFAULT =
            new SimulationContext(EventEngine.getInstance());

    private final EventEngine eventEngine;
    private final List<SimulationAssertion> assertions = new ArrayList<>();

    public SimulationContext() {
        this(new EventEngine());
    }

    public SimulationContext(EventEngine eventEngine) {
        if (eventEngine == null) {
            throw new IllegalArgumentException("Simulation event engine cannot be null");
        }
        this.eventEngine = eventEngine;
    }

    public static SimulationContext defaultContext() {
        return DEFAULT;
    }

    public EventEngine getEventEngine() {
        return eventEngine;
    }

    public void reset() {
        eventEngine.resetEngine();
        assertions.clear();
    }

    public void addAssertion(SimulationAssertion assertion) {
        if (assertion == null) {
            throw new IllegalArgumentException("Simulation assertion cannot be null");
        }
        assertions.add(assertion);
    }

    public void runSimulation() {
        long start = System.currentTimeMillis();
        while (stepSimulation()) {
        }
        eventEngine.recordComputeTime(System.currentTimeMillis() - start);
    }

    public boolean stepSimulation() {
        long nextEventTime = eventEngine.getNextEventTime();
        for (SimulationAssertion assertion : assertions) {
            if (assertion.getTime() < nextEventTime
                    && assertion.getTime() <= eventEngine.getMaxSimulationTime()) {
                assertion.check();
            }
        }
        return eventEngine.stepSimulation();
    }

    public List<SimulationAssertion> getAssertions() {
        return Collections.unmodifiableList(assertions);
    }
}
