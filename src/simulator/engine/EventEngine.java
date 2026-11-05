package simulator.engine;

import java.util.PriorityQueue;

public class EventEngine {
    private static EventEngine instance;
    private final PriorityQueue<SignalEvent> eventQueue;
    private long currentTime;
    private long maxSimulationTime;
    
    private long totalEventsProcessed;
    private int peakQueueDepth;
    private long computeTimeMillis;

    public EventEngine() {
        this.eventQueue = new PriorityQueue<>();
        this.currentTime = 0;
        this.maxSimulationTime = 1000; 
    }

    public static EventEngine getInstance() {
        if (instance == null) instance = new EventEngine();
        return instance;
    }

    public void resetEngine() {
        eventQueue.clear();
        SignalEvent.resetSequence();
        currentTime = 0;
        maxSimulationTime = 1000;
        totalEventsProcessed = 0;
        peakQueueDepth = 0;
        computeTimeMillis = 0;
    }

    public void scheduleEvent(SignalEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Simulation event cannot be null");
        }
        if (event.getExecutionTime() < 0) {
            throw new IllegalArgumentException("Simulation event time cannot be negative");
        }
        eventQueue.add(event);
    }

    public void runSimulation() {
        long wallClockStart = System.currentTimeMillis();
        while (stepSimulation()) {
        }
        computeTimeMillis = System.currentTimeMillis() - wallClockStart;
    }

    public boolean stepSimulation() {
        if (eventQueue.isEmpty()) {
            return false;
        }
        if (eventQueue.size() > peakQueueDepth) {
            peakQueueDepth = eventQueue.size();
        }
        if (eventQueue.peek().getExecutionTime() > maxSimulationTime) {
            eventQueue.clear();
            return false;
        }
        SignalEvent currentEvent = eventQueue.poll();
        currentTime = currentEvent.getExecutionTime();
        currentEvent.getTargetWire().drive(currentEvent.getDriver(), currentEvent.getNewState());
        totalEventsProcessed++;
        return true;
    }

    public boolean hasPendingEvents() {
        return !eventQueue.isEmpty();
    }

    public int getPendingEventCount() {
        return eventQueue.size();
    }

    public long getNextEventTime() {
        return eventQueue.isEmpty() ? Long.MAX_VALUE : eventQueue.peek().getExecutionTime();
    }

    public long getCurrentTime() { return currentTime; }
    public long getMaxSimulationTime() { return maxSimulationTime; }
    public void setMaxSimulationTime(long time) {
        if (time < 0) {
            throw new IllegalArgumentException("Maximum simulation time cannot be negative");
        }
        this.maxSimulationTime = time;
    }
    
    public long getTotalEventsProcessed() { return totalEventsProcessed; }
    public int getPeakQueueDepth() { return peakQueueDepth; }
    public long getComputeTimeMillis() { return computeTimeMillis; }
    void recordComputeTime(long millis) { computeTimeMillis = millis; }
}