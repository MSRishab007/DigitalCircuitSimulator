package simulator.schematic;

import simulator.components.AndGate;
import simulator.components.Clock;
import simulator.components.Module;
import simulator.components.LogicGate;
import simulator.components.NandGate;
import simulator.components.NorGate;
import simulator.components.NotGate;
import simulator.components.OrGate;
import simulator.components.SequentialComponent;
import simulator.components.TriStateBuffer;
import simulator.components.XorGate;
import simulator.components.XnorGate;
import simulator.components.FunctionalBlock;
import simulator.core.DigitalComponent;
import simulator.core.InputPin;
import simulator.core.OutputPin;
import simulator.core.SignalObserver;
import simulator.core.VoltageState;
import simulator.core.Wire;
import simulator.engine.StaticTimingAnalyzer;
import simulator.parser.NetlistParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SchematicLayout {
    public enum Kind { INPUT, OUTPUT, AND, OR, NOT, NAND, NOR, XOR, XNOR, FUNC, MODULE, BUFFER, DFF, CLOCK, BUS, OTHER, ROUTE }

    private static final int MARGIN = 24;
    private static final int NODE_GAP = 22;
    private static final int ROUTE_HEIGHT = 8;
    private static final int TRACK_SPACING = 12;
    private static final int CHANNEL_PADDING = 18;
    private static final int LANE_SPACING = 14;
    private static final int LABEL_CHAR_WIDTH = 6;
    private static final int PIN_SPACING = 24;

    public static final class Point {
        public final int x;
        public final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class Node {
        public final Kind kind;
        public final String id;
        public String detail;
        public int inputCount;
        public int outputCount = 1;
        public final List<String> members = new ArrayList<>();
        public String typeName = "";
        public boolean onCriticalPath;
        public final List<Node> previous = new ArrayList<>();
        public final List<Node> next = new ArrayList<>();
        int layer;
        int order;
        double center;
        public int x;
        public int y;
        public int width;
        public int height;

        Node(Kind kind, String id, String detail, int inputCount, int width, int height) {
            this.kind = kind;
            this.id = id;
            this.detail = detail;
            this.inputCount = inputCount;
            this.width = width;
            this.height = height;
        }

        public int outputY(int pin) {
            if (outputCount <= 1) {
                return y;
            }
            return y + (int) Math.round((pin - (outputCount - 1) / 2.0) * PIN_SPACING);
        }

        public int inputY(int pin) {
            if (kind == Kind.BUS || inputCount <= 1) {
                return y;
            }
            return y + (int) Math.round((pin - (inputCount - 1) / 2.0) * PIN_SPACING);
        }
    }

    public static final class Segment {
        public final int x1;
        public final int y1;
        public final int x2;
        public final int y2;

        Segment(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    public static final class Sink {
        final Node node;
        final int pin;

        Sink(Node node, int pin) {
            this.node = node;
            this.pin = pin;
        }
    }

    public static final class Net {
        public final String wire;
        final Node source;
        final List<Sink> sinks = new ArrayList<>();
        public final List<Segment> segments = new ArrayList<>();
        public final List<Point> junctions = new ArrayList<>();
        final Map<Integer, Node> routeByLayer = new HashMap<>();
        boolean labelled;
        public Point labelAt;
        public boolean onCriticalPath;
        int outputPin;

        Net(String wire, Node source) {
            this.wire = wire;
            this.source = source;
        }

        int yAt(Node node) {
            return node == source ? source.outputY(outputPin) : node.y;
        }
    }

    private static final class ChannelEntry {
        final Net net;
        Integer sourceY;
        final List<Integer> targetYs = new ArrayList<>();
        boolean usesLane;
        int trackX;

        ChannelEntry(Net net) {
            this.net = net;
        }

        boolean isStraight() {
            return sourceY != null && !usesLane && targetYs.stream().allMatch(sourceY::equals);
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<Net> nets = new ArrayList<>();
    private final Map<String, String> faults = new HashMap<>();
    private final Map<String, String> drivenBy = new HashMap<>();
    private final Map<String, List<String>> feeds = new HashMap<>();
    private final List<List<Node>> layers = new ArrayList<>();
    private final List<String> criticalPath = new ArrayList<>();
    private int unusedWires;
    private int width;
    private int height;

    private SchematicLayout() { }

    public static SchematicLayout build(NetlistParser parser) {
        return build(parser, false);
    }

    public static SchematicLayout build(NetlistParser parser, boolean collapseModules) {
        SchematicLayout layout = new SchematicLayout();
        layout.createGraph(parser, collapseModules);
        layout.assignLayers();
        layout.addRoutes();
        layout.orderLayers();
        layout.placeVertically();
        layout.placeHorizontallyAndRoute();
        layout.markCriticalPath(parser);
        return layout;
    }

    public List<Node> getNodes() { return Collections.unmodifiableList(nodes); }
    public List<Net> getNets() { return Collections.unmodifiableList(nets); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getUnusedWires() { return unusedWires; }
    public List<String> getCriticalPath() { return Collections.unmodifiableList(criticalPath); }
    public String getFault(String wire) { return faults.get(wire); }
    public String getDriver(String wire) { return drivenBy.get(wire); }
    public List<String> getFeeds(String wire) { return feeds.getOrDefault(wire, List.of()); }

    private void markCriticalPath(NetlistParser parser) {
        try {
            criticalPath.addAll(new StaticTimingAnalyzer().analyze(parser.getComponents()).getCriticalPath());
        } catch (IllegalStateException loop) {
            return;
        }
        for (Node node : nodes) {
            for (String member : node.members) {
                node.onCriticalPath |= criticalPath.contains(member);
            }
        }
        for (Net net : nets) {
            for (Sink sink : net.sinks) {
                for (int i = 0; i + 1 < criticalPath.size(); i++) {
                    net.onCriticalPath |= net.source.members.contains(criticalPath.get(i))
                            && sink.node.members.contains(criticalPath.get(i + 1));
                }
            }
        }
    }

    private static void collectMembers(Module module, Map<DigitalComponent, Node> byComponent, Node node) {
        for (DigitalComponent child : module.getSubComponents()) {
            if (child instanceof Module) {
                collectMembers((Module) child, byComponent, node);
            } else {
                byComponent.put(child, node);
                node.members.add(child.getComponentId());
            }
        }
    }

    private void createGraph(NetlistParser parser, boolean collapseModules) {
        Map<DigitalComponent, Node> byComponent = new IdentityHashMap<>();
        if (collapseModules) {
            for (Module module : parser.getModules()) {
                Node node = new Node(Kind.MODULE, module.getComponentId(), "Macro " + module.getTypeName(), 0, 0, 0);
                node.typeName = module.getTypeName();
                collectMembers(module, byComponent, node);
                nodes.add(node);
            }
        }
        Map<Wire, List<Node>> drivers = new IdentityHashMap<>();
        for (DigitalComponent component : parser.getComponents()) {
            Node node = byComponent.get(component);
            if (node == null) {
                node = componentNode(component);
                node.members.add(component.getComponentId());
                byComponent.put(component, node);
                nodes.add(node);
            }
            for (OutputPin pin : component.getOutputPins()) {
                if (pin.getConnectedWire() != null) {
                    List<Node> wireDrivers = drivers.computeIfAbsent(pin.getConnectedWire(), key -> new ArrayList<>());
                    if (!wireDrivers.contains(node)) {
                        wireDrivers.add(node);
                    }
                }
            }
        }

        Map<Node, Integer> moduleInputs = new IdentityHashMap<>();
        Map<Node, Integer> moduleOutputs = new IdentityHashMap<>();
        for (Wire wire : parser.getAllWires()) {
            String name = wire.getWireId();
            if (wire.getStuckAtState() != null) {
                faults.put(name, wire.getStuckAtState() == VoltageState.HIGH ? "stuck at 1" : "stuck at 0");
            }
            List<Node> wireDrivers = drivers.getOrDefault(wire, List.of());
            List<Sink> sinks = new ArrayList<>();
            Map<Node, Integer> modulePin = new IdentityHashMap<>();
            for (SignalObserver observer : wire.getObservers()) {
                if (observer instanceof InputPin) {
                    InputPin pin = (InputPin) observer;
                    DigitalComponent parent = pin.getParentComponent();
                    Node target = byComponent.get(parent);
                    if (target == null) {
                        continue;
                    }
                    if (target.kind == Kind.MODULE) {
                        if (wireDrivers.size() == 1 && wireDrivers.get(0) == target) {
                            feeds.computeIfAbsent(name, key -> new ArrayList<>()).add(parent.getComponentId());
                            continue;
                        }
                        if (!modulePin.containsKey(target)) {
                            int index = moduleInputs.getOrDefault(target, 0);
                            moduleInputs.put(target, index + 1);
                            modulePin.put(target, index);
                            sinks.add(new Sink(target, index));
                        }
                    } else {
                        sinks.add(new Sink(target, parent.getInputPins().indexOf(pin)));
                    }
                    feeds.computeIfAbsent(name, key -> new ArrayList<>()).add(parent.getComponentId());
                }
            }
            boolean internal = wireDrivers.size() == 1 && wireDrivers.get(0).kind == Kind.MODULE && sinks.isEmpty()
                    && !wire.getObservers().isEmpty() && hasPinObserver(wire);
            if (internal) {
                continue;
            }
            if (wireDrivers.isEmpty() && sinks.isEmpty()) {
                unusedWires++;
                continue;
            }
            Node source;
            int outputPin = 0;
            if (wireDrivers.isEmpty()) {
                source = new Node(Kind.INPUT, name, "Input", 0, portWidth(name, wire), 24);
                nodes.add(source);
            } else if (wireDrivers.size() == 1) {
                source = wireDrivers.get(0);
                drivenBy.put(name, driverName(wire, parser, source));
                if (source.kind == Kind.MODULE) {
                    outputPin = moduleOutputs.getOrDefault(source, 0);
                    moduleOutputs.put(source, outputPin + 1);
                }
            } else {
                source = new Node(Kind.BUS, name, "Shared wire", wireDrivers.size(), 10, 10);
                nodes.add(source);
                List<String> ids = new ArrayList<>();
                for (Node driver : wireDrivers) {
                    Net branch = new Net(name, driver);
                    if (driver.kind == Kind.MODULE) {
                        branch.outputPin = moduleOutputs.getOrDefault(driver, 0);
                        moduleOutputs.put(driver, branch.outputPin + 1);
                    }
                    branch.sinks.add(new Sink(source, 0));
                    nets.add(branch);
                    ids.add(driver.id);
                }
                drivenBy.put(name, String.join(", ", ids));
            }
            if (sinks.isEmpty()) {
                Node output = new Node(Kind.OUTPUT, name, "Output", 1, portWidth(name, wire), 24);
                nodes.add(output);
                sinks.add(new Sink(output, 0));
            }
            Net net = new Net(name, source);
            net.outputPin = outputPin;
            net.sinks.addAll(sinks);
            net.labelled = source.kind != Kind.INPUT
                    && !sinks.stream().allMatch(sink -> sink.node.kind == Kind.OUTPUT);
            nets.add(net);
        }
        for (Node node : nodes) {
            if (node.kind == Kind.MODULE) {
                node.inputCount = moduleInputs.getOrDefault(node, 0);
                node.outputCount = Math.max(1, moduleOutputs.getOrDefault(node, 0));
                node.width = Math.max(96, Math.max(node.typeName.length() * 8, node.id.length() * LABEL_CHAR_WIDTH) + 24);
                node.height = Math.max(node.inputCount, node.outputCount) * PIN_SPACING + 36;
                node.detail = "Macro " + node.typeName + " containing " + String.join(", ", node.members);
            }
        }
    }

    private static boolean hasPinObserver(Wire wire) {
        for (SignalObserver observer : wire.getObservers()) {
            if (observer instanceof InputPin) {
                return true;
            }
        }
        return false;
    }

    private static String driverName(Wire wire, NetlistParser parser, Node source) {
        for (DigitalComponent component : parser.getComponents()) {
            for (OutputPin pin : component.getOutputPins()) {
                if (pin.getConnectedWire() == wire) {
                    return component.getComponentId();
                }
            }
        }
        return source.id;
    }

    private static int portWidth(String name, Wire wire) {
        int faultText = wire.getStuckAtState() == null ? 0 : 66;
        return name.length() * 7 + 40 + faultText;
    }

    private static Node componentNode(DigitalComponent component) {
        String id = component.getComponentId();
        int inputs = component.getInputPins().size();
        int width = Math.max(64, id.length() * LABEL_CHAR_WIDTH + 8);
        Kind kind = kindOf(component);
        String delay = component instanceof LogicGate
                ? ", delay " + ((LogicGate) component).getPropagationDelay() + " ns" : "";
        int height = Math.max(66, inputs * PIN_SPACING + 30);
        String detail = kind == Kind.FUNC
                ? "Function " + ((FunctionalBlock) component).getExpressionText() + delay
                : kind == Kind.DFF ? "D flip-flop" : kind == Kind.CLOCK ? "Clock"
                : kind == Kind.BUFFER ? "Tri-state buffer" + delay : kind.name() + " gate" + delay;
        return new Node(kind, id, detail, inputs, width, height);
    }

    public static Kind kindOf(DigitalComponent component) {
        if (component instanceof FunctionalBlock) return Kind.FUNC;
        if (component instanceof XnorGate) return Kind.XNOR;
        if (component instanceof AndGate) return Kind.AND;
        if (component instanceof OrGate) return Kind.OR;
        if (component instanceof NotGate) return Kind.NOT;
        if (component instanceof NandGate) return Kind.NAND;
        if (component instanceof NorGate) return Kind.NOR;
        if (component instanceof XorGate) return Kind.XOR;
        if (component instanceof TriStateBuffer) return Kind.BUFFER;
        if (component instanceof SequentialComponent) return Kind.DFF;
        if (component instanceof Clock) return Kind.CLOCK;
        return Kind.OTHER;
    }

    private void assignLayers() {
        Map<Node, List<Node>> successors = new IdentityHashMap<>();
        Map<Node, Integer> incoming = new IdentityHashMap<>();
        for (Node node : nodes) {
            successors.put(node, new ArrayList<>());
            incoming.put(node, 0);
        }
        for (Net net : nets) {
            for (Sink sink : net.sinks) {
                successors.get(net.source).add(sink.node);
                incoming.merge(sink.node, 1, (a, b) -> a + b);
            }
        }

        List<Node> starts = new ArrayList<>();
        for (Node node : nodes) {
            if (incoming.get(node) == 0) {
                starts.add(node);
            }
        }
        starts.sort(Comparator.comparingInt(node -> node.kind == Kind.INPUT ? 0 : 1));
        starts.addAll(nodes);

        Set<Node> done = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> active = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Node, Set<Node>> loopEdges = new IdentityHashMap<>();
        for (Node start : starts) {
            findLoopEdges(start, successors, done, active, loopEdges);
        }

        Map<Node, Integer> remaining = new IdentityHashMap<>();
        for (Node node : nodes) {
            remaining.put(node, 0);
        }
        for (Node node : nodes) {
            for (Node successor : successors.get(node)) {
                if (!isLoopEdge(loopEdges, node, successor)) {
                    remaining.merge(successor, 1, (a, b) -> a + b);
                }
            }
        }
        List<Node> ready = new ArrayList<>();
        for (Node node : nodes) {
            if (remaining.get(node) == 0) {
                ready.add(node);
            }
        }
        for (int i = 0; i < ready.size(); i++) {
            Node node = ready.get(i);
            for (Node successor : successors.get(node)) {
                if (!isLoopEdge(loopEdges, node, successor)) {
                    successor.layer = Math.max(successor.layer, node.layer + 1);
                    if (remaining.merge(successor, -1, (a, b) -> a + b) == 0) {
                        ready.add(successor);
                    }
                }
            }
        }

        int last = 0;
        for (Node node : nodes) {
            if (node.kind != Kind.OUTPUT) {
                last = Math.max(last, node.layer);
            }
        }
        for (Node node : nodes) {
            if (node.kind == Kind.OUTPUT) {
                node.layer = last + 1;
            }
        }
    }

    private static boolean isLoopEdge(Map<Node, Set<Node>> loopEdges, Node from, Node to) {
        return loopEdges.getOrDefault(from, Set.of()).contains(to);
    }

    private static void findLoopEdges(Node node, Map<Node, List<Node>> successors, Set<Node> done,
                                      Set<Node> active, Map<Node, Set<Node>> loopEdges) {
        if (done.contains(node)) {
            return;
        }
        active.add(node);
        for (Node successor : successors.get(node)) {
            if (active.contains(successor)) {
                loopEdges.computeIfAbsent(node, key -> new HashSet<>()).add(successor);
            } else {
                findLoopEdges(successor, successors, done, active, loopEdges);
            }
        }
        active.remove(node);
        done.add(node);
    }

    private void addRoutes() {
        int layerCount = 0;
        for (Node node : nodes) {
            layerCount = Math.max(layerCount, node.layer + 1);
        }
        for (int i = 0; i < layerCount; i++) {
            layers.add(new ArrayList<>());
        }
        for (Node node : nodes) {
            layers.get(node.layer).add(node);
        }
        for (Net net : nets) {
            net.routeByLayer.put(net.source.layer, net.source);
            int furthest = net.source.layer;
            for (Sink sink : net.sinks) {
                furthest = Math.max(furthest, sink.node.layer);
            }
            Node previous = net.source;
            for (int layer = net.source.layer + 1; layer < furthest; layer++) {
                Node route = new Node(Kind.ROUTE, net.wire, "", 1, 0, ROUTE_HEIGHT);
                route.layer = layer;
                layers.get(layer).add(route);
                net.routeByLayer.put(layer, route);
                link(previous, route);
                previous = route;
            }
            for (Sink sink : net.sinks) {
                if (sink.node.layer > net.source.layer) {
                    link(net.routeByLayer.get(sink.node.layer - 1), sink.node);
                }
            }
        }
    }

    private static void link(Node from, Node to) {
        from.next.add(to);
        to.previous.add(from);
    }

    private void orderLayers() {
        renumber();
        for (int sweep = 0; sweep < 8; sweep++) {
            for (int i = 1; i < layers.size(); i++) {
                sortByNeighbours(layers.get(i), true);
            }
            for (int i = layers.size() - 2; i >= 0; i--) {
                sortByNeighbours(layers.get(i), false);
            }
        }
    }

    private void sortByNeighbours(List<Node> layer, boolean usePrevious) {
        if (layer.stream().anyMatch(node -> node.kind == Kind.INPUT)) {
            return;
        }
        Map<Node, Double> keys = new IdentityHashMap<>();
        for (Node node : layer) {
            List<Node> neighbours = usePrevious ? node.previous : node.next;
            keys.put(node, neighbours.isEmpty() ? node.order
                    : neighbours.stream().mapToInt(other -> other.order).average().orElse(node.order));
        }
        layer.sort(Comparator.comparingDouble(keys::get));
        for (int i = 0; i < layer.size(); i++) {
            layer.get(i).order = i;
        }
    }

    private void renumber() {
        for (List<Node> layer : layers) {
            for (int i = 0; i < layer.size(); i++) {
                layer.get(i).order = i;
            }
        }
    }

    private void placeVertically() {
        for (List<Node> layer : layers) {
            double y = 0;
            for (Node node : layer) {
                node.center = y + node.height / 2.0;
                y += node.height + NODE_GAP;
            }
        }
        for (int sweep = 0; sweep < 6; sweep++) {
            for (int i = 1; i < layers.size(); i++) {
                alignLayer(layers.get(i), true);
            }
            for (int i = layers.size() - 2; i >= 0; i--) {
                alignLayer(layers.get(i), false);
            }
        }
        double top = Double.MAX_VALUE;
        for (List<Node> layer : layers) {
            for (Node node : layer) {
                top = Math.min(top, node.center - node.height / 2.0);
            }
        }
        for (List<Node> layer : layers) {
            for (Node node : layer) {
                node.y = (int) Math.round(node.center - top) + MARGIN;
            }
        }
    }

    private static void alignLayer(List<Node> layer, boolean usePrevious) {
        if (layer.isEmpty()) {
            return;
        }
        double[] wanted = new double[layer.size()];
        for (int i = 0; i < wanted.length; i++) {
            Node node = layer.get(i);
            List<Node> neighbours = usePrevious ? node.previous : node.next;
            wanted[i] = neighbours.isEmpty() ? node.center
                    : neighbours.stream().mapToDouble(other -> other.center).average().orElse(node.center);
        }
        double[] placed = new double[wanted.length];
        double error = 0;
        for (int i = 0; i < placed.length; i++) {
            placed[i] = wanted[i];
            if (i > 0) {
                double minimum = placed[i - 1] + (layer.get(i - 1).height + layer.get(i).height) / 2.0 + NODE_GAP;
                placed[i] = Math.max(placed[i], minimum);
            }
            error += wanted[i] - placed[i];
        }
        double shift = error / placed.length;
        for (int i = 0; i < placed.length; i++) {
            layer.get(i).center = placed[i] + shift;
        }
    }

    private void placeHorizontallyAndRoute() {
        int bottom = MARGIN;
        for (List<Node> layer : layers) {
            for (Node node : layer) {
                bottom = Math.max(bottom, node.y + node.height / 2);
            }
        }

        List<Map<Net, ChannelEntry>> channels = new ArrayList<>();
        for (int i = 0; i <= layers.size(); i++) {
            channels.add(new LinkedHashMap<>());
        }
        Map<Net, Integer> lanes = new IdentityHashMap<>();
        for (Net net : nets) {
            for (Sink sink : net.sinks) {
                if (sink.node.layer > net.source.layer) {
                    int channel = sink.node.layer - 1;
                    Node from = net.routeByLayer.get(channel);
                    ChannelEntry entry = entry(channels, channel, net);
                    entry.sourceY = net.yAt(from);
                    entry.targetYs.add(sink.node.inputY(sink.pin));
                    for (int c = net.source.layer; c < channel; c++) {
                        ChannelEntry through = entry(channels, c, net);
                        through.sourceY = net.yAt(net.routeByLayer.get(c));
                        through.targetYs.add(net.routeByLayer.get(c + 1).y);
                    }
                } else {
                    if (!lanes.containsKey(net)) {
                        lanes.put(net, bottom + LANE_SPACING * (lanes.size() + 2));
                    }
                    ChannelEntry leaving = entry(channels, net.source.layer, net);
                    leaving.sourceY = net.yAt(net.source);
                    leaving.usesLane = true;
                    ChannelEntry arriving = entry(channels, sink.node.layer - 1, net);
                    arriving.usesLane = true;
                    arriving.targetYs.add(sink.node.inputY(sink.pin));
                }
            }
        }

        int cursor = MARGIN;
        int[] columnX = new int[layers.size()];
        int[] columnWidth = new int[layers.size()];
        cursor = assignTracks(channels.get(0), cursor, null);
        for (int i = 0; i < layers.size(); i++) {
            columnX[i] = cursor;
            for (Node node : layers.get(i)) {
                columnWidth[i] = Math.max(columnWidth[i], node.width);
            }
            for (Node node : layers.get(i)) {
                if (node.kind == Kind.ROUTE) {
                    node.width = columnWidth[i];
                }
                node.x = columnX[i] + (columnWidth[i] - node.width) / 2;
            }
            cursor += columnWidth[i];
            cursor = assignTracks(channels.get(i + 1), cursor, layers.get(i));
        }
        width = cursor + MARGIN;
        height = (lanes.isEmpty() ? bottom : Collections.max(lanes.values())) + MARGIN;

        for (Net net : nets) {
            drawNet(net, channels, lanes, columnX);
        }
    }

    private static ChannelEntry entry(List<Map<Net, ChannelEntry>> channels, int channel, Net net) {
        return channels.get(channel + 1).computeIfAbsent(net, ChannelEntry::new);
    }

    private static int assignTracks(Map<Net, ChannelEntry> channel, int startX, List<Node> leftColumn) {
        int labelSpace = 0;
        List<ChannelEntry> pending = new ArrayList<>();
        for (ChannelEntry entry : channel.values()) {
            if (entry.net.labelled && leftColumn != null && leftColumn.contains(entry.net.source)) {
                labelSpace = Math.max(labelSpace, entry.net.wire.length() * LABEL_CHAR_WIDTH + 8);
            }
            if (!entry.isStraight()) {
                pending.add(entry);
            }
        }
        pending.sort(Comparator.comparingInt(entry -> entry.sourceY == null ? Integer.MAX_VALUE : entry.sourceY));

        int x = startX + CHANNEL_PADDING + labelSpace;
        while (!pending.isEmpty()) {
            ChannelEntry chosen = pending.get(0);
            for (ChannelEntry candidate : pending) {
                boolean blocked = false;
                for (ChannelEntry other : pending) {
                    blocked |= other != candidate && other.sourceY != null
                            && candidate.targetYs.contains(other.sourceY);
                }
                if (!blocked) {
                    chosen = candidate;
                    break;
                }
            }
            chosen.trackX = x;
            x += TRACK_SPACING;
            pending.remove(chosen);
        }
        return Math.max(x, startX + CHANNEL_PADDING + labelSpace + TRACK_SPACING) + CHANNEL_PADDING / 2;
    }

    private void drawNet(Net net, List<Map<Net, ChannelEntry>> channels, Map<Net, Integer> lanes, int[] columnX) {
        Node source = net.source;
        if (net.labelled) {
            net.labelAt = new Point(source.x + source.width + 4, net.yAt(source) - 4);
        }
        for (Node route : net.routeByLayer.values()) {
            if (route.kind == Kind.ROUTE) {
                net.segments.add(new Segment(route.x, route.y, route.x + route.width, route.y));
            }
        }
        Integer laneY = lanes.get(net);
        int laneLeft = Integer.MAX_VALUE;
        int laneRight = Integer.MIN_VALUE;
        for (int c = -1; c < layers.size(); c++) {
            ChannelEntry entry = channels.get(c + 1).get(net);
            if (entry == null) {
                continue;
            }
            if (entry.isStraight()) {
                Node from = net.routeByLayer.get(c);
                for (Sink sink : net.sinks) {
                    if (sink.node.layer == c + 1) {
                        net.segments.add(new Segment(from.x + from.width, net.yAt(from), sink.node.x, net.yAt(from)));
                    }
                }
                Node onward = net.routeByLayer.get(c + 1);
                if (onward != null && onward != from) {
                    net.segments.add(new Segment(from.x + from.width, net.yAt(from), onward.x, net.yAt(from)));
                }
                continue;
            }
            List<Integer> ys = new ArrayList<>(entry.targetYs);
            if (entry.sourceY != null) {
                Node from = net.routeByLayer.get(c);
                net.segments.add(new Segment(from.x + from.width, entry.sourceY, entry.trackX, entry.sourceY));
                ys.add(entry.sourceY);
            }
            for (Sink sink : net.sinks) {
                if (sink.node.layer == c + 1) {
                    net.segments.add(new Segment(entry.trackX, sink.node.inputY(sink.pin),
                            sink.node.x, sink.node.inputY(sink.pin)));
                }
            }
            Node onward = net.routeByLayer.get(c + 1);
            if (onward != null && onward.kind == Kind.ROUTE) {
                net.segments.add(new Segment(entry.trackX, onward.y, onward.x, onward.y));
            }
            if (entry.usesLane && laneY != null) {
                ys.add(laneY);
                laneLeft = Math.min(laneLeft, entry.trackX);
                laneRight = Math.max(laneRight, entry.trackX);
            }
            int low = Collections.min(ys);
            int high = Collections.max(ys);
            if (low != high) {
                net.segments.add(new Segment(entry.trackX, low, entry.trackX, high));
            }
            for (int y : new HashSet<>(ys)) {
                if (y > low && y < high) {
                    net.junctions.add(new Point(entry.trackX, y));
                }
            }
        }
        if (laneY != null && laneLeft < laneRight) {
            net.segments.add(new Segment(laneLeft, laneY, laneRight, laneY));
        }
    }
}