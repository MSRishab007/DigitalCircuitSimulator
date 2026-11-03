package simulator;

import org.teavm.jso.JSExport;
import simulator.parser.SimulationRunner;

public class WasmBridge {
    public static void main(String[] args) {
    }

    @JSExport
    public static String runSimulation(String netlist) {
        try {
            SimulationRunner.SimulationResult result =
                    new SimulationRunner().runString(netlist, null);
            return "Simulation Complete!\n" +
                   "Wires mapped: " + result.getWireCount() + "\n" +
                   "Components loaded: " + result.getComponentCount() + "\n" +
                   "Events processed: " + result.getEventsProcessed() + "\n" +
                   "Max critical path delay: " + result.getCriticalPathDelay() + "ns";
                   
        } catch (Exception e) {
            return "SIMULATION ERROR: " + e.getMessage();
        }
    }
}