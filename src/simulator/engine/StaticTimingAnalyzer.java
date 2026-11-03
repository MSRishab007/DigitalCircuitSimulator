package simulator.engine;

import simulator.core.DigitalComponent;
import simulator.core.InputPin;
import simulator.core.SignalObserver;
import simulator.components.LogicGate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StaticTimingAnalyzer {
    private final Map<DigitalComponent, Integer> pathDelays = new HashMap<>();

    public void analyze(List<DigitalComponent> components) {
        int criticalPathDelay = 0;
        
        for (DigitalComponent comp : components) {
            criticalPathDelay = Math.max(criticalPathDelay, calculateMaxPath(comp));
        }

        System.out.println("\n[+] STATIC TIMING ANALYSIS (STA)");
        System.out.println("-------------------------------------------------");
        System.out.println(String.format("%-25s %d ns", "Critical Path Delay:", criticalPathDelay));
        
        if (criticalPathDelay > 0) {
            // 1000 / ns = MHz
            double maxFreqMHz = 1000.0 / criticalPathDelay;
            System.out.println(String.format("%-25s %.2f MHz", "Max Safe Clock Freq:", maxFreqMHz));
        }
        System.out.println("-------------------------------------------------");
    }

    private int calculateMaxPath(DigitalComponent current) {
        if (pathDelays.containsKey(current)) return pathDelays.get(current);

        int maxDownstreamDelay = 0;
        
        for (var outPin : current.getOutputPins()) {
            var connectedWire = outPin.getConnectedWire();
            if (connectedWire != null) {
                for (SignalObserver obs : connectedWire.getObservers()) {
                    if (obs instanceof InputPin) {
                        DigitalComponent nextComp = ((InputPin) obs).getParentComponent();
                        maxDownstreamDelay = Math.max(maxDownstreamDelay, calculateMaxPath(nextComp));
                    }
                }
            }
        }

        int myDelay = (current instanceof LogicGate) ? ((LogicGate) current).getPropagationDelay() : 0;
        int totalDelay = myDelay + maxDownstreamDelay;
        pathDelays.put(current, totalDelay);
        return totalDelay;
    }
}