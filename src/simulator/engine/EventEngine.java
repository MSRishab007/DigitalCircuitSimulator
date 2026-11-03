package simulator.engine;

import java.util.PriorityQueue;

public class EventEngine {
    private static EventEngine instance;
    private final PriorityQueue<SignalEvent> eventQueue;
    private long currentTime;
    private long maxSimulationTime;
    
    // Telemetry Metrics
    private long totalEventsProcessed;
    private int peakQueueDepth;
    private long computeTimeMillis;

    private EventEngine() {
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
        currentTime = 0;
        totalEventsProcessed = 0;
        peakQueueDepth = 0;
        computeTimeMillis = 0;
    }

    public void scheduleEvent(SignalEvent event) {
        eventQueue.add(event);
    }

    public void runSimulation() {
        long wallClockStart = System.currentTimeMillis();

        while (!eventQueue.isEmpty()) {
            if (eventQueue.size() > peakQueueDepth) {
                peakQueueDepth = eventQueue.size();
            }

            SignalEvent currentEvent = eventQueue.poll();

            if (currentEvent.getExecutionTime() > maxSimulationTime) {
                break;
            }

            currentTime = currentEvent.getExecutionTime();
            currentEvent.getTargetWire().drive(currentEvent.getDriver(), currentEvent.getNewState());
            totalEventsProcessed++;
        }

        computeTimeMillis = System.currentTimeMillis() - wallClockStart;
    }

    // Getters
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
}