package simulator.io;

import simulator.core.SignalObserver;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.EventEngine;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class VcdLogger implements SignalObserver {
    private final String filepath;
    private final List<Wire> monitoredWires;
    private final List<String> transitionBuffer;

    public VcdLogger(String filepath) {
        this.filepath = filepath;
        this.monitoredWires = new ArrayList<>();
        this.transitionBuffer = new ArrayList<>();
    }

    public void monitorWire(Wire wire) {
        monitoredWires.add(wire);
        wire.addObserver(this);
    }

    @Override
    public void update(Wire source, VoltageState newState) {
        long currentTime = EventEngine.getInstance().getCurrentTime();
        String vcdState = formatState(newState);
        transitionBuffer.add("#" + currentTime);
        transitionBuffer.add(vcdState + source.getWireId());
    }

    private String formatState(VoltageState state) {
        switch (state) {
            case HIGH: return "1";
            case LOW: return "0";
            case HIGH_Z: return "z";
            default: return "x";
        }
    }

    public void exportFile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filepath))) {
            writer.println("$date Today $end");
            writer.println("$timescale 1ns $end");
            
            // Define variables
            for (Wire wire : monitoredWires) {
                writer.println("$var wire 1 " + wire.getWireId() + " " + wire.getWireId() + " $end");
            }
            writer.println("$enddefinitions $end");
            
            // Dump transitions
            for (String transition : transitionBuffer) {
                writer.println(transition);
            }
            System.out.println("Waveform successfully exported to " + filepath);
        }
    }
}