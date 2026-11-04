package simulator.gui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class WaveformPanel extends JPanel implements Scrollable {
    // Inner class to map time to a physical voltage state
    private static class Transition {
        final long time;
        final char state;

        Transition(long time, char state) {
            this.time = time;
            this.state = state;
        }
    }

    private static final int ROW_HEIGHT = 46;
    private static final int HEADER_HEIGHT = 28;
    private static final int RIGHT_PADDING = 36;
    private static final int LEVEL_HIGH = 9;
    private static final int LEVEL_MID = 22;
    private static final int LEVEL_LOW = 35;
    private static final int SNAP_PIXELS = 6;
    private static final double MAX_PIXELS_PER_NS = 200;

    private static final Color GRID = new Color(0xE6E8EB);
    private static final Color RULE = new Color(0xD0D4D9);
    private static final Color TEXT = new Color(0x1F2328);
    private static final Color MUTED = new Color(0x5F6770);
    private static final Color HIGH = new Color(0x1B7F3B);
    private static final Color LOW = new Color(0xB3261E);
    private static final Color HIGH_Z = new Color(0xB8500B);
    private static final Color UNKNOWN_FILL = new Color(0xECEEF1);
    private static final Color CURSOR = new Color(0x1D5FBF);
    private static final Color MARKER = new Color(0xB8500B);

    private final Map<String, List<Transition>> signals = new LinkedHashMap<>();
    private final Font labelFont = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private final Font axisFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private long maxTime = 0;
    private String filter = "";
    private boolean fitToWidth = true;
    private double zoomPixelsPerNs = 1;
    private Long cursorTime;
    private Long markerTime;
    private boolean exporting;
    private Consumer<String> readoutListener = text -> { };

    public WaveformPanel() {
        setBackground(Color.WHITE);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                moveCursor(e.getX());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                moveCursor(e.getX());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                cursorTime = null;
                publishReadout();
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                moveCursor(e.getX());
                if (SwingUtilities.isLeftMouseButton(e) && cursorTime != null) {
                    markerTime = cursorTime;
                    publishReadout();
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown() || e.isMetaDown()) {
                    zoomAround(e.getX(), e.getWheelRotation() < 0 ? 1.25 : 0.8);
                } else if (getParent() != null) {
                    getParent().dispatchEvent(SwingUtilities.convertMouseEvent(
                            WaveformPanel.this, e, getParent()));
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    /** Scrolling repaints the frozen names and time axis, so the viewport must not copy old pixels. */
    @Override
    public void addNotify() {
        super.addNotify();
        if (getParent() instanceof JViewport) {
            ((JViewport) getParent()).setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        }
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

        if (markerTime != null && markerTime > maxTime) {
            markerTime = null;
        }
        cursorTime = null;
        publishReadout();
        // Force the canvas to recalculate and redraw
        revalidate();
        repaint();
    }

    public void setReadoutListener(Consumer<String> listener) {
        readoutListener = listener;
        publishReadout();
    }

    /** Shows only the signals whose name contains the text, ignoring case. */
    public void setFilter(String text) {
        filter = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        revalidate();
        repaint();
    }

    public void zoomIn() {
        zoomAround(getVisibleRect().x + getVisibleRect().width / 2, 1.5);
    }

    public void zoomOut() {
        zoomAround(getVisibleRect().x + getVisibleRect().width / 2, 1 / 1.5);
    }

    public void fitToWindow() {
        fitToWidth = true;
        revalidate();
        repaint();
    }

    public void clearMarker() {
        markerTime = null;
        publishReadout();
        repaint();
    }

    public boolean hasSignals() {
        return !signals.isEmpty();
    }

    public void exportPng(File file) throws IOException {
        int width = Math.max(getWidth(), 400);
        int height = HEADER_HEIGHT + visibleSignals().size() * ROW_HEIGHT + 10;
        BufferedImage image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.scale(2, 2);
        g.setClip(0, 0, width, height);
        exporting = true;
        try {
            paintWaveforms(g, new Rectangle(0, 0, width, height));
        } finally {
            exporting = false;
            g.dispose();
        }
        ImageIO.write(image, "png", file);
    }

    private void zoomAround(int anchorX, double factor) {
        if (signals.isEmpty() || maxTime == 0) {
            return;
        }
        Rectangle visible = getVisibleRect();
        int anchorOffset = anchorX - visible.x;
        double anchorTime = timeAt(anchorX);
        double fitted = fittedPixelsPerNs();
        double next = Math.min(MAX_PIXELS_PER_NS, pixelsPerNs() * factor);
        if (next <= fitted) {
            fitToWindow();
            return;
        }
        fitToWidth = false;
        zoomPixelsPerNs = next;
        revalidate();
        SwingUtilities.invokeLater(() -> {
            int x = xAt(anchorTime) - anchorOffset;
            Rectangle now = getVisibleRect();
            scrollRectToVisible(new Rectangle(x, now.y, now.width, now.height));
            repaint();
        });
    }

    private void moveCursor(int x) {
        Rectangle visible = getVisibleRect();
        if (signals.isEmpty() || x < visible.x + labelWidth()) {
            cursorTime = null;
        } else {
            cursorTime = snap(Math.max(0, Math.min(maxTime, Math.round(timeAt(x)))));
        }
        publishReadout();
        repaint();
    }

    /** Pulls the cursor onto a nearby signal edge, which makes propagation delays easy to measure. */
    private long snap(long time) {
        long best = time;
        double bestDistance = SNAP_PIXELS / Math.max(pixelsPerNs(), 1e-9);
        for (String name : visibleSignals()) {
            for (Transition transition : signals.get(name)) {
                double distance = Math.abs(transition.time - time);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = transition.time;
                }
            }
        }
        return best;
    }

    private void publishReadout() {
        String text;
        if (cursorTime == null && markerTime == null) {
            text = signals.isEmpty() ? " " : "Hover to read values. Click to place a marker. Ctrl+scroll to zoom.";
        } else {
            StringBuilder builder = new StringBuilder();
            if (cursorTime != null) {
                builder.append("Cursor ").append(cursorTime).append(" ns");
            }
            if (markerTime != null) {
                if (builder.length() > 0) {
                    builder.append("     ");
                }
                builder.append("Marker ").append(markerTime).append(" ns");
            }
            if (cursorTime != null && markerTime != null) {
                builder.append("     Difference ").append(Math.abs(cursorTime - markerTime)).append(" ns");
            }
            text = builder.toString();
        }
        readoutListener.accept(text);
    }

    private List<String> visibleSignals() {
        List<String> names = new ArrayList<>();
        for (String name : signals.keySet()) {
            if (filter.isEmpty() || name.toLowerCase(Locale.ROOT).contains(filter)) {
                names.add(name);
            }
        }
        return names;
    }

    private int labelWidth() {
        FontMetrics metrics = getFontMetrics(labelFont);
        int widest = 0;
        for (String name : signals.keySet()) {
            widest = Math.max(widest, metrics.stringWidth(name));
        }
        return Math.max(110, widest + 44);
    }

    private double fittedPixelsPerNs() {
        int available = getParent() instanceof JViewport ? getParent().getWidth() : getWidth();
        return Math.max(0.0001, (available - labelWidth() - RIGHT_PADDING) / (double) Math.max(1, maxTime));
    }

    private double pixelsPerNs() {
        return fitToWidth ? fittedPixelsPerNs() : zoomPixelsPerNs;
    }

    private int xAt(double time) {
        return labelWidth() + (int) Math.round(time * pixelsPerNs());
    }

    private double timeAt(int x) {
        return (x - labelWidth()) / pixelsPerNs();
    }

    @Override
    public Dimension getPreferredSize() {
        if (signals.isEmpty()) {
            return new Dimension(400, 200);
        }
        int width = fitToWidth ? 400 : labelWidth() + (int) Math.ceil(maxTime * zoomPixelsPerNs) + RIGHT_PADDING;
        return new Dimension(width, HEADER_HEIGHT + visibleSignals().size() * ROW_HEIGHT + 10);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height - ROW_HEIGHT : visibleRect.width / 2;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return fitToWidth;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport && getPreferredSize().height < getParent().getHeight();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        paintWaveforms(g2, getVisibleRect());
        g2.dispose();
    }

    private void paintWaveforms(Graphics2D g2, Rectangle view) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRect(view.x, view.y, view.width, view.height);

        if (signals.isEmpty()) {
            g2.setFont(axisFont.deriveFont(13f));
            g2.setColor(MUTED);
            g2.drawString("Run a simulation to generate waveforms.", view.x + 16, view.y + 28);
            return;
        }

        List<String> names = visibleSignals();
        int labels = labelWidth();
        int contentBottom = HEADER_HEIGHT + names.size() * ROW_HEIGHT;
        double scale = pixelsPerNs();
        long step = tickStep(scale);
        long firstTick = Math.max(0, (long) Math.floor(timeAt(view.x + labels) / step)) * step;
        long lastTime = Math.min(maxTime, (long) Math.ceil(timeAt(view.x + view.width)));

        // Traces and grid scroll underneath the frozen names and time axis.
        Shape outer = g2.getClip();
        g2.clipRect(view.x + labels, view.y + HEADER_HEIGHT, view.width - labels, view.height - HEADER_HEIGHT);
        g2.setColor(GRID);
        for (long t = firstTick; t <= maxTime && t <= lastTime; t += step) {
            g2.drawLine(xAt(t), 0, xAt(t), contentBottom);
        }
        for (int row = 0; row < names.size(); row++) {
            int y = HEADER_HEIGHT + row * ROW_HEIGHT;
            if (y + ROW_HEIGHT >= view.y && y <= view.y + view.height) {
                paintSignal(g2, signals.get(names.get(row)), y, view);
            }
        }
        if (!exporting && cursorTime != null) {
            g2.setColor(CURSOR);
            g2.drawLine(xAt(cursorTime), 0, xAt(cursorTime), contentBottom);
        }
        if (markerTime != null) {
            g2.setColor(MARKER);
            g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 3}, 0));
            g2.drawLine(xAt(markerTime), 0, xAt(markerTime), contentBottom);
            g2.setStroke(new BasicStroke(1));
        }
        g2.setClip(outer);

        // Frozen signal names, with the value at the cursor beside each one.
        g2.setColor(Color.WHITE);
        g2.fillRect(view.x, view.y, labels, view.height);
        g2.setFont(labelFont);
        FontMetrics labelMetrics = g2.getFontMetrics();
        Long readAt = !exporting && cursorTime != null ? cursorTime : markerTime;
        for (int row = 0; row < names.size(); row++) {
            int y = HEADER_HEIGHT + row * ROW_HEIGHT;
            int baseline = y + LEVEL_MID + labelMetrics.getAscent() / 2 - 1;
            g2.setColor(TEXT);
            g2.drawString(names.get(row), view.x + 10, baseline);
            if (readAt != null) {
                char state = stateAt(signals.get(names.get(row)), readAt);
                g2.setColor(colorOf(state));
                g2.drawString(String.valueOf(Character.toUpperCase(state)), view.x + labels - 20, baseline);
            }
            g2.setColor(GRID);
            g2.drawLine(view.x, y + ROW_HEIGHT - 1, view.x + labels, y + ROW_HEIGHT - 1);
        }
        g2.setColor(RULE);
        g2.drawLine(view.x + labels, view.y, view.x + labels, view.y + view.height);

        // Frozen time axis.
        g2.setColor(Color.WHITE);
        g2.fillRect(view.x, view.y, view.width, HEADER_HEIGHT);
        g2.setFont(axisFont);
        FontMetrics axisMetrics = g2.getFontMetrics();
        g2.setColor(MUTED);
        g2.drawString("Signal", view.x + 10, view.y + 18);
        Shape beforeAxis = g2.getClip();
        g2.clipRect(view.x + labels, view.y, view.width - labels, HEADER_HEIGHT);
        for (long t = firstTick; t <= maxTime && t <= lastTime; t += step) {
            String label = t + " ns";
            g2.drawString(label, xAt(t) - (t == 0 ? 0 : axisMetrics.stringWidth(label) / 2), view.y + 18);
        }
        g2.setClip(beforeAxis);
        g2.setColor(RULE);
        g2.drawLine(view.x, view.y + HEADER_HEIGHT - 1, view.x + view.width, view.y + HEADER_HEIGHT - 1);
    }

    private static long tickStep(double pixelsPerNs) {
        double wanted = 80 / pixelsPerNs;
        long magnitude = 1;
        while (magnitude * 10 <= wanted) {
            magnitude *= 10;
        }
        for (long multiple : new long[]{1, 2, 5, 10}) {
            if (magnitude * multiple >= wanted) {
                return magnitude * multiple;
            }
        }
        return magnitude * 10;
    }

    private char stateAt(List<Transition> transitions, long time) {
        char state = 'x';
        for (Transition transition : transitions) {
            if (transition.time > time) {
                break;
            }
            state = transition.state;
        }
        return state;
    }

    private void paintSignal(Graphics2D g2, List<Transition> transitions, int y, Rectangle view) {
        if (transitions.isEmpty()) {
            return;
        }
        for (int i = 0; i < transitions.size(); i++) {
            Transition current = transitions.get(i);
            long end = i + 1 < transitions.size() ? transitions.get(i + 1).time : maxTime;
            int x1 = xAt(current.time);
            int x2 = xAt(end);
            if (x2 >= view.x && x1 <= view.x + view.width) {
                drawSegment(g2, x1, x2, y, current.state);
                if (i > 0) {
                    drawVerticalEdge(g2, x1, y, transitions.get(i - 1).state, current.state);
                }
            }
        }
    }

    private static int levelOf(char state) {
        return state == '1' ? LEVEL_HIGH : state == '0' ? LEVEL_LOW : LEVEL_MID;
    }

    private static Color colorOf(char state) {
        if (state == '1') {
            return HIGH;
        }
        if (state == '0') {
            return LOW;
        }
        return state == 'z' || state == 'Z' ? HIGH_Z : MUTED;
    }

    private void drawSegment(Graphics2D g2, int x1, int x2, int y, char state) {
        g2.setColor(colorOf(state));
        if (state == '1' || state == '0') {
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(x1, y + levelOf(state), x2, y + levelOf(state));
        } else if (state == 'z' || state == 'Z') {
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 4}, 0));
            g2.drawLine(x1, y + LEVEL_MID, x2, y + LEVEL_MID);
        } else {
            // Unknown: a shaded band, so it cannot be mistaken for a real logic level.
            g2.setColor(UNKNOWN_FILL);
            g2.fillRect(x1, y + LEVEL_HIGH, Math.max(1, x2 - x1), LEVEL_LOW - LEVEL_HIGH);
            if (x2 - x1 > 24) {
                g2.setColor(MUTED);
                g2.setFont(axisFont);
                g2.drawString("X", (x1 + x2) / 2 - 3, y + LEVEL_MID + 4);
            }
        }
        g2.setStroke(new BasicStroke(1));
    }

    private void drawVerticalEdge(Graphics2D g2, int x, int y, char oldState, char newState) {
        g2.setColor(TEXT);
        g2.setStroke(new BasicStroke(1));
        g2.drawLine(x, y + levelOf(oldState), x, y + levelOf(newState));
    }
}
