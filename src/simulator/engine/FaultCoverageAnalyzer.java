package simulator.engine;

import simulator.components.Clock;
import simulator.core.DigitalComponent;
import simulator.core.SignalObserver;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.parser.NetlistParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FaultCoverageAnalyzer {
    public static final class Result {
        private final int totalFaults;
        private final List<String> observedWires;
        private final List<String> undetected;

        private Result(int totalFaults, List<String> observedWires, List<String> undetected) {
            this.totalFaults = totalFaults;
            this.observedWires = Collections.unmodifiableList(observedWires);
            this.undetected = Collections.unmodifiableList(undetected);
        }

        public int getTotalFaults() { return totalFaults; }
        public int getDetectedFaults() { return totalFaults - undetected.size(); }
        public List<String> getObservedWires() { return observedWires; }
        public List<String> getUndetected() { return undetected; }

        public double getCoveragePercent() {
            return totalFaults == 0 ? 0 : Math.round(getDetectedFaults() * 1000.0 / totalFaults) / 10.0;
        }

        public String describe() {
            StringBuilder text = new StringBuilder("Fault coverage: ").append(getDetectedFaults()).append(" of ")
                    .append(totalFaults).append(" stuck-at faults detected (").append(getCoveragePercent()).append("%)\n")
                    .append("Observed wires: ").append(String.join(", ", observedWires)).append('\n');
            if (undetected.isEmpty()) {
                text.append("Your stimuli detect every fault.");
            } else {
                text.append("Not detected by your stimuli:");
                for (String fault : undetected) {
                    text.append("\n  ").append(fault);
                }
            }
            return text.toString();
        }
    }

    private static final class Recorder implements SignalObserver {
        private final Map<String, TreeMap<Long, VoltageState>> traces;
        private final EventEngine engine;

        private Recorder(Map<String, TreeMap<Long, VoltageState>> traces, EventEngine engine) {
            this.traces = traces;
            this.engine = engine;
        }

        @Override
        public void update(Wire source, VoltageState newState) {
            traces.get(source.getWireId()).put(engine.getCurrentTime(), newState);
        }
    }

    public Result analyze(String netlist) {
        NetlistParser structure = new NetlistParser(new SimulationContext());
        structure.parseString(netlist);
        CircuitPorts ports = new CircuitPorts(structure);
        Set<String> observed = new LinkedHashSet<>(ports.getOutputs());
        observed.addAll(ports.getStateWires());
        if (observed.isEmpty()) {
            throw new IllegalArgumentException("Fault coverage needs an output wire to observe, "
                    + "but every wire in this netlist feeds another component");
        }
        Map<String, TreeMap<Long, VoltageState>> healthy = simulate(netlist, observed, null, null);
        int total = 0;
        List<String> undetected = new ArrayList<>();
        for (Wire wire : structure.getAllWires()) {
            if (wire.getStuckAtState() != null) {
                continue;
            }
            for (VoltageState fault : new VoltageState[]{VoltageState.LOW, VoltageState.HIGH}) {
                total++;
                if (!differs(healthy, simulate(netlist, observed, wire.getWireId(), fault))) {
                    undetected.add(wire.getWireId() + " stuck at " + (fault == VoltageState.HIGH ? "1" : "0"));
                }
            }
        }
        return new Result(total, new ArrayList<>(observed), undetected);
    }

    private static Map<String, TreeMap<Long, VoltageState>> simulate(String netlist, Set<String> observed,
                                                                     String faultWire, VoltageState fault) {
        SimulationContext context = new SimulationContext();
        NetlistParser parser = new NetlistParser(context);
        parser.parseString(netlist);
        Map<String, TreeMap<Long, VoltageState>> traces = new HashMap<>();
        Recorder recorder = new Recorder(traces, context.getEventEngine());
        for (String name : observed) {
            Wire wire = parser.getWire(name);
            TreeMap<Long, VoltageState> trace = new TreeMap<>();
            trace.put(0L, wire.getCurrentState());
            traces.put(name, trace);
            wire.addObserver(recorder);
        }
        if (faultWire != null) {
            parser.getWire(faultWire).injectStuckAtFault(fault);
            if (traces.containsKey(faultWire)) {
                traces.get(faultWire).put(0L, fault);
            }
        }
        for (DigitalComponent component : parser.getComponents()) {
            if (component instanceof Clock) {
                ((Clock) component).generateClockPulses();
            }
        }
        context.runSimulation();
        return traces;
    }

    private static boolean differs(Map<String, TreeMap<Long, VoltageState>> healthy,
                                   Map<String, TreeMap<Long, VoltageState>> faulty) {
        for (Map.Entry<String, TreeMap<Long, VoltageState>> entry : healthy.entrySet()) {
            TreeMap<Long, VoltageState> first = entry.getValue();
            TreeMap<Long, VoltageState> second = faulty.get(entry.getKey());
            TreeSet<Long> times = new TreeSet<>(first.keySet());
            times.addAll(second.keySet());
            for (Long time : times) {
                VoltageState a = first.floorEntry(time).getValue();
                VoltageState b = second.floorEntry(time).getValue();
                boolean known = (a == VoltageState.HIGH || a == VoltageState.LOW)
                        && (b == VoltageState.HIGH || b == VoltageState.LOW);
                if (known && a != b) {
                    return true;
                }
            }
        }
        return false;
    }
}
