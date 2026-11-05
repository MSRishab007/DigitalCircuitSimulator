package simulator.gui;

import simulator.schematic.SchematicLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

/** Draws a {@link SchematicLayout}, colouring wires by the values passed to {@link #setValues}. */
final class SchematicPanel extends JPanel implements Scrollable {
    private static final Color TEXT = new Color(0x1F2328);
    private static final Color MUTED = new Color(0x5F6770);
    private static final Color LINE = new Color(0x3C444D);
    private static final Color PORT_BORDER = new Color(0xB9BEC5);
    private static final Color HOVER = new Color(0x1D5FBF);
    private static final Color HIGH = new Color(0x1B7F3B);
    private static final Color LOW = new Color(0xB3261E);
    private static final Color HIGH_Z = new Color(0xB8500B);
    private static final Color UNKNOWN = new Color(0x8A9199);
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    private static final Font ID_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font TYPE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 9);
    private static final Font PORT_FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private static final int SYMBOL_WIDTH = 44;
    private static final int SYMBOL_HEIGHT = 36;
    private static final int HIT_DISTANCE = 5;
    private static final double SMALLEST_AUTOMATIC_SCALE = 0.45;
    private static final String HINT = "Hover over a wire or gate for details.";

    private SchematicLayout circuit;
    private String message = "Open this tab with a netlist in the editor to draw its schematic.";
    private Function<String, Character> values;
    private Consumer<String> readoutListener = text -> { };
    private double scale = 1;
    private boolean fitting = true;
    private double fitFloor = SMALLEST_AUTOMATIC_SCALE;
    private String hoveredWire;
    private SchematicLayout.Node hoveredNode;

    SchematicPanel() {
        setBackground(Color.WHITE);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX() / scale, e.getY() / scale);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoveredWire = null;
                hoveredNode = null;
                publishReadout();
                repaint();
            }

            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                if (e.isControlDown() || e.isMetaDown()) {
                    setScale(scale * (e.getWheelRotation() < 0 ? 1.15 : 1 / 1.15));
                } else if (getParent() != null) {
                    getParent().dispatchEvent(SwingUtilities.convertMouseEvent(SchematicPanel.this, e, getParent()));
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (getParent() instanceof JViewport) {
            getParent().addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (fitting) {
                        applyFit();
                    }
                }
            });
        }
    }

    void setReadoutListener(Consumer<String> listener) {
        readoutListener = listener;
        publishReadout();
    }

    void setCircuit(SchematicLayout layout) {
        boolean first = circuit == null;
        circuit = layout;
        message = null;
        hoveredNode = null;
        if (first || fitting) {
            fitting = true;
            applyFit();
        }
        publishReadout();
        revalidate();
        repaint();
    }

    void showMessage(String text) {
        circuit = null;
        message = text;
        hoveredNode = null;
        hoveredWire = null;
        publishReadout();
        revalidate();
        repaint();
    }

    /** Sets the source of wire values as 0, 1, x or z, or null to draw plain wires. */
    void setValues(Function<String, Character> source) {
        values = source;
        publishReadout();
        repaint();
    }

    boolean hasCircuit() {
        return circuit != null;
    }

    void zoomIn() {
        setScale(scale * 1.25);
    }

    void zoomOut() {
        setScale(scale / 1.25);
    }

    void fitToWindow() {
        fitting = true;
        fitFloor = 0.05;
        applyFit();
    }

    void exportPng(File file) throws IOException {
        BufferedImage image = new BufferedImage(circuit.getWidth() * 2, circuit.getHeight() * 2,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.scale(2, 2);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, circuit.getWidth(), circuit.getHeight());
        String savedWire = hoveredWire;
        SchematicLayout.Node savedNode = hoveredNode;
        hoveredWire = null;
        hoveredNode = null;
        paintCircuit(g);
        hoveredWire = savedWire;
        hoveredNode = savedNode;
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    private void setScale(double value) {
        fitting = false;
        fitFloor = SMALLEST_AUTOMATIC_SCALE;
        scale = Math.max(0.05, Math.min(3, value));
        revalidate();
        repaint();
    }

    private void applyFit() {
        if (circuit == null || !(getParent() instanceof JViewport)) {
            return;
        }
        Dimension view = getParent().getSize();
        if (view.width <= 0 || view.height <= 0) {
            return;
        }
        double wanted = Math.min((view.width - 2.0) / circuit.getWidth(), (view.height - 2.0) / circuit.getHeight());
        scale = Math.max(fitFloor, Math.min(1.2, wanted));
        revalidate();
        repaint();
    }

    private void updateHover(double x, double y) {
        if (circuit == null) {
            return;
        }
        SchematicLayout.Node node = null;
        for (SchematicLayout.Node candidate : circuit.getNodes()) {
            if (x >= candidate.x && x <= candidate.x + candidate.width
                    && Math.abs(y - candidate.y) <= candidate.height / 2.0) {
                node = candidate;
            }
        }
        String wire = null;
        if (node != null && (node.kind == SchematicLayout.Kind.INPUT || node.kind == SchematicLayout.Kind.OUTPUT
                || node.kind == SchematicLayout.Kind.BUS)) {
            wire = node.id;
            node = null;
        } else if (node == null) {
            double reach = HIT_DISTANCE / scale;
            for (SchematicLayout.Net net : circuit.getNets()) {
                for (SchematicLayout.Segment s : net.segments) {
                    if (java.awt.geom.Line2D.ptSegDist(s.x1, s.y1, s.x2, s.y2, x, y) <= reach) {
                        wire = net.wire;
                    }
                }
            }
        }
        if (node != hoveredNode || (wire == null ? hoveredWire != null : !wire.equals(hoveredWire))) {
            hoveredNode = node;
            hoveredWire = wire;
            publishReadout();
            repaint();
        }
    }

    private void publishReadout() {
        readoutListener.accept(readoutText());
    }

    private String readoutText() {
        if (circuit == null) {
            return " ";
        }
        if (hoveredNode != null) {
            return hoveredNode.id + ": " + hoveredNode.detail;
        }
        if (hoveredWire != null) {
            StringBuilder text = new StringBuilder(hoveredWire);
            Character value = valueOf(hoveredWire);
            if (value != null) {
                text.append(" = ").append(Character.toUpperCase(value));
            }
            String fault = circuit.getFault(hoveredWire);
            if (fault != null) {
                text.append(" (").append(fault).append(')');
            }
            String driver = circuit.getDriver(hoveredWire);
            text.append(". ").append(driver == null ? "Input" : "Driven by " + driver);
            if (!circuit.getFeeds(hoveredWire).isEmpty()) {
                text.append(", feeds ").append(String.join(", ", circuit.getFeeds(hoveredWire)));
            }
            return text.toString();
        }
        String unused = circuit.getUnusedWires() == 0 ? ""
                : " " + circuit.getUnusedWires() + " unconnected wire"
                + (circuit.getUnusedWires() == 1 ? " is" : "s are") + " not shown.";
        return (values == null ? "Run a simulation to colour wires by value. " : "") + HINT + unused;
    }

    private Character valueOf(String wire) {
        return values == null ? null : values.apply(wire);
    }

    private static Color colorOf(Character value) {
        if (value == null) {
            return LINE;
        }
        switch (value) {
            case '1': return HIGH;
            case '0': return LOW;
            case 'z': case 'Z': return HIGH_Z;
            default: return UNKNOWN;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (circuit == null) {
            return new Dimension(400, 200);
        }
        return new Dimension((int) Math.ceil(circuit.getWidth() * scale), (int) Math.ceil(circuit.getHeight() * scale));
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
        return orientation == SwingConstants.VERTICAL ? visibleRect.height / 2 : visibleRect.width / 2;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport && getPreferredSize().width <= getParent().getWidth();
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport && getPreferredSize().height <= getParent().getHeight();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        if (circuit == null) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(ID_FONT.deriveFont(13f));
            g.setColor(MUTED);
            if (message != null) {
                drawWrapped(g, message, 16, 28, Math.max(200, getVisibleRect().width - 32));
            }
        } else {
            g.scale(scale, scale);
            paintCircuit(g);
        }
        g.dispose();
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth) {
        FontMetrics metrics = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int lineY = y;
        for (String word : text.split(" ")) {
            if (line.length() > 0 && metrics.stringWidth(line + " " + word) > maxWidth) {
                g.drawString(line.toString(), x, lineY);
                lineY += metrics.getHeight();
                line.setLength(0);
            }
            line.append(line.length() == 0 ? "" : " ").append(word);
        }
        g.drawString(line.toString(), x, lineY);
    }

    private void paintCircuit(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        for (SchematicLayout.Net net : circuit.getNets()) {
            if (!net.wire.equals(hoveredWire)) {
                paintNet(g, net);
            }
        }
        for (SchematicLayout.Net net : circuit.getNets()) {
            if (net.wire.equals(hoveredWire)) {
                paintNet(g, net);
            }
        }
        for (SchematicLayout.Node node : circuit.getNodes()) {
            paintNode(g, node);
        }
        g.setFont(LABEL_FONT);
        for (SchematicLayout.Net net : circuit.getNets()) {
            if (net.labelAt != null) {
                g.setColor(net.wire.equals(hoveredWire) ? HOVER : MUTED);
                g.drawString(net.wire, net.labelAt.x, net.labelAt.y);
            }
        }
    }

    private void paintNet(Graphics2D g, SchematicLayout.Net net) {
        Character value = valueOf(net.wire);
        boolean hovered = net.wire.equals(hoveredWire);
        g.setColor(colorOf(value));
        if (value != null && (value == 'z' || value == 'Z')) {
            g.setStroke(new BasicStroke(hovered ? 2.6f : 1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    1, new float[]{5, 4}, 0));
        } else {
            g.setStroke(new BasicStroke(hovered ? 2.6f : 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
        for (SchematicLayout.Segment s : net.segments) {
            g.drawLine(s.x1, s.y1, s.x2, s.y2);
        }
        for (SchematicLayout.Point junction : net.junctions) {
            g.fill(new Ellipse2D.Float(junction.x - 3f, junction.y - 3f, 6f, 6f));
        }
    }

    private void paintNode(Graphics2D g, SchematicLayout.Node node) {
        boolean hovered = node == hoveredNode;
        switch (node.kind) {
            case INPUT:
            case OUTPUT:
                paintPort(g, node);
                break;
            case BUS:
                g.setColor(colorOf(valueOf(node.id)));
                g.fill(new Ellipse2D.Float(node.x, node.y - 5f, 10f, 10f));
                break;
            case ROUTE:
                break;
            default:
                paintComponentNode(g, node, hovered);
                break;
        }
    }

    private void paintPort(Graphics2D g, SchematicLayout.Node node) {
        boolean hovered = node.id.equals(hoveredWire);
        Character value = valueOf(node.id);
        int top = node.y - node.height / 2;
        g.setColor(Color.WHITE);
        g.fillRoundRect(node.x, top, node.width, node.height, 6, 6);
        g.setStroke(new BasicStroke(hovered ? 2f : 1f));
        g.setColor(hovered ? HOVER : PORT_BORDER);
        g.drawRoundRect(node.x, top, node.width, node.height, 6, 6);
        g.setFont(PORT_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int baseline = node.y + metrics.getAscent() / 2 - 1;
        g.setColor(TEXT);
        g.drawString(node.id, node.x + 8, baseline);
        int textEnd = node.x + 8 + metrics.stringWidth(node.id);
        String fault = circuit.getFault(node.id);
        if (fault != null) {
            g.setFont(LABEL_FONT);
            g.setColor(HIGH_Z);
            g.drawString(fault, textEnd + 6, baseline);
            g.setFont(PORT_FONT);
        }
        if (value != null) {
            g.setColor(colorOf(value));
            g.drawString(String.valueOf(Character.toUpperCase(value)), node.x + node.width - 16, baseline);
        }
    }

    private void paintComponentNode(Graphics2D g, SchematicLayout.Node node, boolean hovered) {
        int left = node.x + (node.width - SYMBOL_WIDTH) / 2;
        int top = node.y - SYMBOL_HEIGHT / 2;

        g.setStroke(new BasicStroke(1.4f));
        g.setColor(LINE);
        for (int pin = 0; pin < node.inputCount; pin++) {
            g.drawLine(node.x, node.inputY(pin), left + 10, node.inputY(pin));
        }
        g.drawLine(left + SYMBOL_WIDTH - 4, node.y, node.x + node.width, node.y);

        g.translate(left, top);
        Shape shape = symbol(node.kind);
        g.setColor(Color.WHITE);
        g.fill(shape);
        g.setColor(hovered ? HOVER : TEXT);
        g.setStroke(new BasicStroke(hovered ? 2.2f : 1.5f));
        g.draw(shape);
        paintSymbolDetails(g, node.kind, node.inputCount);
        g.translate(-left, -top);

        g.setFont(ID_FONT);
        g.setColor(hovered ? HOVER : MUTED);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(node.id, node.x + (node.width - metrics.stringWidth(node.id)) / 2,
                node.y + node.height / 2 - 3);
    }

    private static void paintSymbolDetails(Graphics2D g, SchematicLayout.Kind kind, int inputCount) {
        g.setFont(TYPE_FONT);
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(MUTED);
        switch (kind) {
            case AND:
                g.drawString("AND", 8, 22);
                break;
            case OR:
                g.drawString("OR", 12, 22);
                break;
            case XOR:
                g.drawString("XOR", 13, 22);
                break;
            case NAND:
                drawBubble(g, 38);
                g.setColor(MUTED);
                g.drawString("NAND", 3, 22);
                break;
            case NOR:
                drawBubble(g, 38);
                g.setColor(MUTED);
                g.drawString("NOR", 8, 22);
                break;
            case NOT:
                drawBubble(g, 34);
                break;
            case BUFFER:
                g.drawString("en", 3, 28);
                break;
            case DFF:
                g.setColor(TEXT);
                g.drawString("D", 4, 10);
                g.drawString("Q", SYMBOL_WIDTH - 9 - metrics.stringWidth("Q"), 22);
                Path2D clock = new Path2D.Float();
                clock.moveTo(0, 25);
                clock.lineTo(7, 30);
                clock.lineTo(0, 35);
                g.setStroke(new BasicStroke(1.2f));
                g.draw(clock);
                break;
            case CLOCK:
                Path2D pulse = new Path2D.Float();
                pulse.moveTo(7, 25);
                pulse.lineTo(14, 25);
                pulse.lineTo(14, 11);
                pulse.lineTo(25, 11);
                pulse.lineTo(25, 25);
                pulse.lineTo(36, 25);
                g.setColor(TEXT);
                g.setStroke(new BasicStroke(1.4f));
                g.draw(pulse);
                break;
            case OTHER:
                g.drawString("?", 20, 22);
                break;
            default:
                break;
        }
    }

    private static void drawBubble(Graphics2D g, int x) {
        Ellipse2D bubble = new Ellipse2D.Float(x, 15, 6, 6);
        g.setColor(Color.WHITE);
        g.fill(bubble);
        g.setColor(TEXT);
        g.draw(bubble);
    }

    private static Shape symbol(SchematicLayout.Kind kind) {
        switch (kind) {
            case AND:
                return andShape(44);
            case NAND:
                return andShape(38);
            case OR:
                return orShape(0, 44);
            case NOR:
                return orShape(0, 38);
            case XOR:
                Path2D xor = orShape(6, 44);
                xor.moveTo(0, 0);
                xor.quadTo(10, 18, 0, 36);
                return xor;
            case NOT:
                return triangle(34);
            case BUFFER:
                return triangle(SYMBOL_WIDTH);
            default:
                return new Rectangle(0, 0, SYMBOL_WIDTH - 4, SYMBOL_HEIGHT);
        }
    }

    private static Path2D andShape(float width) {
        float body = width * 0.45f;
        Path2D path = new Path2D.Float();
        path.moveTo(0, 0);
        path.lineTo(body, 0);
        float control = body + (width - body) / 0.75f;
        path.curveTo(control, 0, control, 36, body, 36);
        path.lineTo(0, 36);
        path.closePath();
        return path;
    }

    private static Path2D orShape(float start, float end) {
        Path2D path = new Path2D.Float();
        float span = end - start;
        path.moveTo(start, 0);
        path.curveTo(start + span * 0.4, 0, start + span * 0.78, 6, end, 18);
        path.curveTo(start + span * 0.78, 30, start + span * 0.4, 36, start, 36);
        path.quadTo(start + 10, 18, start, 0);
        path.closePath();
        return path;
    }

    private static Path2D triangle(float width) {
        Path2D path = new Path2D.Float();
        path.moveTo(0, 1);
        path.lineTo(width, 18);
        path.lineTo(0, 35);
        path.closePath();
        return path;
    }
}
