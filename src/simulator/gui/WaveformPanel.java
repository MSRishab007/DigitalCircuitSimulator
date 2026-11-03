package simulator.gui;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.List;

public class WaveformPanel extends JPanel {
    // Inner class to map time to a physical voltage state
    private static class Transition {
        long time;
        char state;
        Transition(long time, char state) {
            this.time = time;
            this.state = state;
        }
    }

    private final Map<String, List<Transition>> signals = new LinkedHashMap<>();
    private long maxTime = 0;

    public WaveformPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(800, 600));
    }

    public void loadVcd(String filepath) {
        signals.clear();
        maxTime = 0;
        Map<String, String> symbolToName = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            long currentTime = 0;
            
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("$var")) {
                    // Parse: $var wire 1 ! Input_A $end
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        symbolToName.put(parts[3], parts[4]);
                        signals.put(parts[4], new ArrayList<>());
                    }
                } else if (line.startsWith("#")) {
                    // Parse timestamp: #50
                    currentTime = Long.parseLong(line.substring(1));
                    maxTime = Math.max(maxTime, currentTime);
                } else if (line.length() >= 2) {
                    // Parse state change: 1! or 0! or z!
                    char state = line.charAt(0);
                    if (state == '0' || state == '1' || state == 'z' || state == 'Z' || state == 'x' || state == 'X') {
                        String symbol = line.substring(1);
                        String name = symbolToName.get(symbol);
                        if (name != null) {
                            signals.get(name).add(new Transition(currentTime, state));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Waveform parse error: " + e.getMessage());
        }
        
        // Force the canvas to recalculate and redraw
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (signals.isEmpty() || maxTime == 0) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Run a simulation to generate waveforms.", 20, 30);
            return;
        }

        int trackHeight = 60;
        int startX = 120;
        int startY = 40;
        
        // Dynamic zoom: scale the width of the waves to fit the panel
        double timeScale = Math.max(1.0, (double)(getWidth() - startX - 40) / maxTime);

        // 1. Draw Grid and Timeline Labels
        g2.setColor(new Color(230, 230, 230));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (long t = 0; t <= maxTime; t += 10) {
            int x = startX + (int)(t * timeScale);
            g2.drawLine(x, startY - 20, x, startY + (signals.size() * trackHeight));
            g2.setColor(Color.GRAY);
            g2.drawString(t + "ns", x - 10, startY - 25);
            g2.setColor(new Color(230, 230, 230));
        }

        // 2. Draw Signals
        int yOffset = startY;
        g2.setFont(new Font("Monospaced", Font.BOLD, 12));
        
        for (Map.Entry<String, List<Transition>> entry : signals.entrySet()) {
            String wireName = entry.getKey();
            List<Transition> trans = entry.getValue();

            // Wire label
            g2.setColor(Color.BLACK);
            g2.drawString(wireName, 10, yOffset + 25);

            if (trans.isEmpty()) {
                yOffset += trackHeight;
                continue;
            }

            char currentState = trans.get(0).state;
            long lastTime = 0;

            for (int i = 1; i <= trans.size(); i++) {
                long nextTime = (i == trans.size()) ? maxTime : trans.get(i).time;
                int x1 = startX + (int)(lastTime * timeScale);
                int x2 = startX + (int)(nextTime * timeScale);
                
                // Draw horizontal state line
                drawSegment(g2, x1, x2, yOffset, currentState);
                
                // Draw vertical edge transition if state changes
                if (i < trans.size()) {
                    char nextState = trans.get(i).state;
                    drawVerticalEdge(g2, x2, yOffset, currentState, nextState);
                    currentState = nextState;
                    lastTime = nextTime;
                }
            }
            yOffset += trackHeight;
        }
        
        setPreferredSize(new Dimension(getWidth(), Math.max(getHeight(), yOffset + 50)));
    }

    private void drawSegment(Graphics2D g2, int x1, int x2, int y, char state) {
        int yHigh = y;
        int yLow = y + 40;
        int yMid = y + 20;
        
        g2.setStroke(new BasicStroke(2));
        if (state == '1') { 
            g2.setColor(new Color(0, 180, 0)); // Green
            g2.drawLine(x1, yHigh, x2, yHigh); 
        } else if (state == '0') { 
            g2.setColor(new Color(200, 0, 0)); // Red
            g2.drawLine(x1, yLow, x2, yLow); 
        } else if (state == 'z' || state == 'Z') {
            g2.setColor(Color.ORANGE);
            g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0));
            g2.drawLine(x1, yMid, x2, yMid); // Dashed
        } else { 
            g2.setColor(Color.GRAY); // Unknown
            g2.drawLine(x1, yMid, x2, yMid); 
        }
    }

    private void drawVerticalEdge(Graphics2D g2, int x, int y, char oldState, char newState) {
        int yHigh = y;
        int yLow = y + 40;
        int yMid = y + 20;
        
        int y1 = (oldState == '1') ? yHigh : (oldState == '0') ? yLow : yMid;
        int y2 = (newState == '1') ? yHigh : (newState == '0') ? yLow : yMid;
        
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1));
        g2.drawLine(x, y1, x, y2);
    }
}