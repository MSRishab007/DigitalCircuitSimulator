package simulator.engine;

public final class SimulationDebugger {
    private final SimulationContext context;

    public SimulationDebugger(SimulationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Simulation context cannot be null");
        }
        this.context = context;
    }

    public boolean step() {
        return context.stepSimulation();
    }

    public void run() {
        context.runSimulation();
    }

    public void reset() {
        context.reset();
    }

    public long getCurrentTime() {
        return context.getEventEngine().getCurrentTime();
    }

    public int getPendingEventCount() {
        return context.getEventEngine().getPendingEventCount();
    }

    public boolean isComplete() {
        return !context.getEventEngine().hasPendingEvents();
    }
}
