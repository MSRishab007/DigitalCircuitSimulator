package simulator.application;

public final class UserGuide {
    public enum Surface { DESKTOP, BROWSER, COMMAND_LINE }

    public interface Sink {
        void contents(String[][] sections);
        void section(String id, String title);
        void heading(String title);
        void paragraph(String text);
        void bullets(String... items);
        void steps(String... items);
        void netlist(String text);
        void code(String text);
        void table(String[] headers, String[]... rows);
    }

    private static final String[][] SECTIONS = {
        {"start", "Quick start"},
        {"tabs", "What each part does"},
        {"netlist", "Writing a netlist"},
        {"commands", "Netlist reference"},
        {"functions", "Functions and state machines"},
        {"macros", "Macros and the component library"},
        {"reading", "Reading the results"},
        {"errors", "Fixing common errors"},
        {"cli", "Command line and self-check"}
    };

    private UserGuide() { }

    public static String html(Surface surface) {
        HtmlSink sink = new HtmlSink();
        write(sink, surface);
        return sink.toString();
    }

    public static String text(Surface surface) {
        TextSink sink = new TextSink();
        write(sink, surface);
        return sink.toString();
    }

    public static void write(Sink sink, Surface surface) {
        sink.contents(SECTIONS);
        quickStart(sink, surface);
        parts(sink, surface);
        writing(sink);
        reference(sink);
        functions(sink);
        macros(sink);
        reading(sink);
        errors(sink);
        commandLine(sink);
    }

    private static void quickStart(Sink sink, Surface surface) {
        sink.section("start", "Quick start");
        switch (surface) {
            case DESKTOP:
                sink.steps("Choose a circuit from `Load example`, or press `Open` to load your own netlist file.",
                        "Press `Run simulation` or F5.",
                        "Read the report in `Telemetry log`, then open `Waveform viewer` and `Schematic`.",
                        "Press `Save` to keep your netlist.");
                break;
            case BROWSER:
                sink.steps("Choose a circuit from `Load example`, or press `Open` to load your own netlist file.",
                        "Press `Run simulation` or Ctrl+Enter.",
                        "Read the report in `Telemetry log`, then open `Waveform viewer` and `Schematic`.",
                        "Press `Save` to download your netlist. Everything runs in your browser, so nothing is uploaded.");
                break;
            default:
                sink.steps("Build the project with `mvn clean package`.",
                        "Run `java -cp target/classes simulator.Main check` to confirm that everything works.",
                        "Run `java -cp target/classes simulator.Main simulate half_adder.netlist output.vcd`.");
                break;
        }
    }

    private static void parts(Sink sink, Surface surface) {
        sink.section("tabs", "What each part does");
        sink.table(new String[]{"Part", "Use it to"},
                new String[]{"Run simulation", "Run the whole netlist, then write the report and waveform."},
                new String[]{"Step event", "Process one signal event at a time. The status bar shows the time and the events still waiting."},
                new String[]{"Reset", "Start stepping again from time 0."},
                new String[]{"Telemetry log", "See the run report, including events processed and the critical path delay."},
                new String[]{"Waveform viewer", "See each wire over time. Hover to read values, and click to place a marker that measures delays. Ctrl+scroll zooms."},
                new String[]{"Schematic", "See the circuit drawn from your netlist. After a run, wires show their values, and the slider moves through time."},
                new String[]{"Analysis", "Generate truth tables and state tables, analyze timing and resources, validate, and compare two circuits."});
        if (surface == Surface.BROWSER) {
            sink.paragraph("Keyboard shortcuts: Ctrl+Enter runs, F10 steps, Ctrl+S saves and Ctrl+O opens.");
        } else if (surface == Surface.DESKTOP) {
            sink.paragraph("Keyboard shortcuts: F5 runs, F10 steps, Shift+F5 resets, Ctrl+S saves, Ctrl+O opens, Ctrl+Z undoes and Ctrl+Y redoes.");
        }
    }

    private static void writing(Sink sink) {
        sink.section("netlist", "Writing a netlist");
        sink.paragraph("A netlist is a text file that lists a circuit's wires and components, one per line. "
                + "The usual file extension is `.netlist`. This one is a complete half adder:");
        sink.netlist(String.join("\n",
                "# Anything after # is a comment",
                "SIM_TIME 200",
                "",
                "WIRE A",
                "WIRE B",
                "WIRE Sum",
                "WIRE Carry",
                "",
                "XOR sum_gate 10 A B Sum",
                "AND carry_gate 10 A B Carry",
                "",
                "STIMULUS 0 A 0",
                "STIMULUS 0 B 0",
                "STIMULUS 50 B 1",
                "STIMULUS 100 A 1"));
        sink.paragraph("Write it in this order:");
        sink.steps("Set the run length with `SIM_TIME`.",
                "Declare every wire with `WIRE`. A wire must be declared before a component uses it.",
                "Add components. Each one names its type, a unique ID, a delay in nanoseconds, its inputs, and finally its output.",
                "Drive the inputs with `STIMULUS` or a `CLOCK`. Without these, every wire stays X and nothing changes.");
        sink.paragraph("Commands and component types can be upper or lower case. Wire names and IDs are case sensitive, "
                + "and can't contain spaces. Times are whole nanoseconds.");
    }

    private static void reference(Sink sink) {
        sink.section("commands", "Netlist reference");
        sink.heading("Commands");
        sink.table(new String[]{"Line", "Meaning"},
                new String[]{"`SIM_TIME 200`", "Stop the run at 200 ns. The default is 1000 ns."},
                new String[]{"`WIRE A`", "Declare a wire named A."},
                new String[]{"`STIMULUS 50 A 1`", "Set A to 1 at 50 ns. Values are 0, 1 or Z."},
                new String[]{"`ASSERT 70 Sum 1`", "Stop with an error unless Sum is 1 at 70 ns. Values are 0, 1, X or Z."},
                new String[]{"`FAULT STUCK_AT_0 A`", "Hold A at 0 for the whole run, whatever drives it. STUCK_AT_1 holds it at 1."});
        sink.heading("Components");
        sink.table(new String[]{"Line", "Meaning"},
                new String[]{"`AND g1 10 A B Y`", "Two-input gate with a 10 ns delay. OR, NAND, NOR, XOR and XNOR work the same way."},
                new String[]{"`NOT g2 10 A Y`", "Inverter with one input."},
                new String[]{"`BUFFER b1 5 Data Enable Y`", "Tri-state buffer. It passes Data while Enable is 1, and releases Y while Enable is 0."},
                new String[]{"`CLOCK clk 20 CLK`", "Clock on wire CLK with a 20 ns period, which must be an even number. It starts low."},
                new String[]{"`DFF f1 5 D CLK Q`", "D flip-flop that captures D on each rising CLK edge. Q starts at 0 and changes 5 ns after the edge."},
                new String[]{"`FUNC f 10 A B Y = A & !B`", "Functional block whose output is the expression. See the next section."});
        sink.paragraph("Only tri-state buffers can share an output wire. If two other gates drive the same wire, the run stops with an error.");
    }

    private static void functions(Sink sink) {
        sink.section("functions", "Functions and state machines");
        sink.paragraph("`FUNC` describes what a block does instead of how it's built. List its inputs and its output, then `=` with spaces around it, then the expression.");
        sink.netlist(String.join("\n",
                "WIRE A",
                "WIRE B",
                "WIRE Cin",
                "WIRE Sum",
                "WIRE Carry",
                "FUNC sum 20 A B Cin Sum = A ^ B ^ Cin",
                "FUNC carry 30 A B Cin Carry = (A & B) | (Cin & (A ^ B))"));
        sink.table(new String[]{"Symbol", "Meaning", "Order"},
                new String[]{"`( )`", "Grouping", "First"},
                new String[]{"`!`", "NOT", "Second"},
                new String[]{"`&`", "AND", "Third"},
                new String[]{"`^`", "XOR", "Fourth"},
                new String[]{"`|`", "OR", "Last"},
                new String[]{"`0` `1`", "Constant values", ""});
        sink.paragraph("To check a functional description against a gate-level one, put one circuit in the editor and the other in the comparison box, then press `Check equivalence`.");
        sink.heading("State machines");
        sink.paragraph("Build a Mealy or Moore machine from three parts: `FUNC` blocks for the transition functions, `DFF` flip-flops for the state, and `FUNC` blocks for the outputs. "
                + "If an output reads only the state, it's a Moore machine. If it also reads the inputs, it's a Mealy machine. The `Up-down counter` example shows this.");
        sink.paragraph("For these circuits, `Generate truth table` makes a state table. Each row gives the inputs and the present state, then the next state and the outputs. "
                + "A column such as `S0+` is the value of S0 after the next clock edge.");
    }

    private static void macros(Sink sink) {
        sink.section("macros", "Macros and the component library");
        sink.paragraph("`DEF` packages gates into a reusable part. Write its name and ports, then its contents, then `ENDDEF`. A macro must be defined before it's used, and it can use other macros.");
        sink.netlist(String.join("\n",
                "DEF MY_HALF A B S C",
                "XOR x 10 A B S",
                "AND a 10 A B C",
                "ENDDEF",
                "",
                "DEF MY_FULL A B CIN S COUT",
                "WIRE s1",
                "WIRE c1",
                "WIRE c2",
                "MY_HALF h1 A B s1 c1",
                "MY_HALF h2 s1 CIN S c2",
                "OR o 10 c1 c2 COUT",
                "ENDDEF",
                "",
                "WIRE X",
                "WIRE Y",
                "WIRE Z",
                "WIRE Total",
                "WIRE Carry",
                "MY_FULL adder X Y Z Total Carry"));
        sink.paragraph("A `WIRE` inside a macro belongs to that copy of the macro. Its full name starts with the instance name, such as `adder_s1` in the example above.");
        sink.heading("Built-in components");
        sink.paragraph("These work without a `DEF`, and each internal gate has a 10 ns delay. Your own `DEF` with the same name replaces the built-in one.");
        sink.table(new String[]{"Line", "Meaning"},
                new String[]{"`HALF_ADDER u1 A B SUM CARRY`", "Adds two bits."},
                new String[]{"`FULL_ADDER u2 A B CIN SUM COUT`", "Adds two bits and a carry."},
                new String[]{"`MUX2 u3 A B SEL Y`", "Y is A while SEL is 0, and B while SEL is 1."},
                new String[]{"`DECODER2 u4 A1 A0 Y0 Y1 Y2 Y3`", "Sets the one output that matches A1 and A0."});
    }

    private static void reading(Sink sink) {
        sink.section("reading", "Reading the results");
        sink.table(new String[]{"Value", "Meaning"},
                new String[]{"1", "High. Drawn in green at the top of the waveform row."},
                new String[]{"0", "Low. Drawn in red at the bottom of the waveform row."},
                new String[]{"X", "Unknown. Nothing has driven the wire yet, an input is unknown, or drivers disagree. Drawn as a grey band."},
                new String[]{"Z", "Disconnected. Drawn as a dashed orange line."});
        sink.paragraph("Short spikes at a change are normal. Gates take time to respond, and inputs that change together are processed one at a time, so a gate can briefly see a mix of old and new values.");
        sink.paragraph("Truth tables ignore `STIMULUS`, `ASSERT` and `SIM_TIME`, and try every input combination until the circuit settles. They allow at most 16 inputs and flip-flops combined.");
        sink.paragraph("`Critical path delay` is the slowest route through the gates. `Maximum clock frequency` follows from it. The resource count lists the components that make up the circuit.");
    }

    private static void errors(Sink sink) {
        sink.section("errors", "Fixing common errors");
        sink.paragraph("Errors name the line at fault. The desktop and browser versions also highlight it in the editor.");
        sink.table(new String[]{"Message", "What to do"},
                new String[]{"Wire not defined: A", "Add `WIRE A` above the line that uses it, and check the spelling and case."},
                new String[]{"Unsupported component type: FOO", "Use a type from the reference, or define a macro with `DEF` before this line."},
                new String[]{"AND expects 2 inputs but received 3", "List the inputs, then the output, with nothing extra."},
                new String[]{"Unclocked combinational loop detected", "A gate's output feeds back to its own input. Break the loop with a `DFF`."},
                new String[]{"is driven by more than one component", "Give each gate its own output wire, or use `BUFFER` for a shared bus."},
                new String[]{"Clock period must be an even number", "Use a period such as 20, which divides into two equal halves."},
                new String[]{"Assertion failed at t=70", "The wire's value at that time wasn't what `ASSERT` expected. Check the waveform at that time."},
                new String[]{"Assertion at t=70 is after the end of the simulation", "Increase `SIM_TIME`, or move the assertion to an earlier time."},
                new String[]{"Every wire stays X", "Add `STIMULUS` lines or a `CLOCK` to drive the circuit's inputs."},
                new String[]{"Could not detect input and output wires", "Type the wire names into the Analysis fields, separated by commas."});
    }

    private static void commandLine(Sink sink) {
        sink.section("cli", "Command line and self-check");
        sink.paragraph("Build once with `mvn clean package`, then run these from the project folder. Run this first to confirm that everything works:");
        sink.code("java -cp target/classes simulator.Main check");
        sink.paragraph("It runs built-in test circuits through every feature and prints PASS or FAIL for each one. Its exit status is 0 only if all of them pass, so scripts and CI can use it too.");
        sink.table(new String[]{"Command", "What it does"},
                new String[]{"`simulate circuit.netlist out.vcd`", "Runs the circuit, writes the waveform, and prints a truth table when the circuit is small enough."},
                new String[]{"`validate circuit.netlist`", "Checks the syntax, loops and shared output wires without running."},
                new String[]{"`analyze circuit.netlist`", "Prints the critical path delay, the maximum clock frequency and the resources."},
                new String[]{"`truth-table circuit.netlist`", "Prints a truth table or state table. Add `A,B Sum,Carry` to choose the wires."},
                new String[]{"`equivalent a.netlist b.netlist`", "Prints EQUIVALENT, or NOT_EQUIVALENT with the first row that differs."},
                new String[]{"`check`", "Runs the self-check."},
                new String[]{"`guide`", "Prints this guide."},
                new String[]{"`help`", "Lists the commands."});
        sink.paragraph("Put `java -cp target/classes simulator.Main` before each command. A failed command prints its reason and exits with status 1. `equivalent` exits with status 2 when the circuits differ.");
        sink.paragraph("To test your own netlist from start to finish, run `validate`, then `simulate`, then `truth-table`. To test the browser engine, run `node scripts/browser-smoke-test.js` after building.");
    }

    private static final class HtmlSink implements Sink {
        private final StringBuilder html = new StringBuilder();

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private static String inline(String text) {
            StringBuilder out = new StringBuilder();
            boolean code = false;
            for (String part : escape(text).split("`", -1)) {
                out.append(code ? "<code>" + part + "</code>" : part);
                code = !code;
            }
            return out.toString();
        }

        @Override
        public void contents(String[][] sections) {
            html.append("<ul class=\"guide-contents\">");
            for (String[] section : sections) {
                html.append("<li><a href=\"#").append(section[0]).append("\">").append(escape(section[1])).append("</a></li>");
            }
            html.append("</ul>");
        }

        @Override
        public void section(String id, String title) {
            html.append("<a name=\"").append(id).append("\" id=\"").append(id).append("\"></a><h2>")
                    .append(escape(title)).append("</h2>");
        }

        @Override
        public void heading(String title) {
            html.append("<h3>").append(escape(title)).append("</h3>");
        }

        @Override
        public void paragraph(String text) {
            html.append("<p>").append(inline(text)).append("</p>");
        }

        private void list(String tag, String[] items) {
            html.append('<').append(tag).append('>');
            for (String item : items) {
                html.append("<li>").append(inline(item)).append("</li>");
            }
            html.append("</").append(tag).append('>');
        }

        @Override
        public void bullets(String... items) {
            list("ul", items);
        }

        @Override
        public void steps(String... items) {
            list("ol", items);
        }

        @Override
        public void netlist(String text) {
            code(text);
        }

        @Override
        public void code(String text) {
            html.append("<pre>").append(escape(text)).append("</pre>");
        }

        @Override
        public void table(String[] headers, String[]... rows) {
            html.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\"><tr>");
            for (String header : headers) {
                html.append("<th align=\"left\">").append(escape(header)).append("</th>");
            }
            html.append("</tr>");
            for (String[] row : rows) {
                html.append("<tr>");
                for (String cell : row) {
                    html.append("<td>").append(inline(cell)).append("</td>");
                }
                html.append("</tr>");
            }
            html.append("</table>");
        }

        @Override
        public String toString() {
            return html.toString();
        }
    }

    private static final class TextSink implements Sink {
        private final StringBuilder text = new StringBuilder();

        private static String plain(String value) {
            return value.replace("`", "");
        }

        @Override
        public void contents(String[][] sections) {
            text.append("HOW TO USE THE SIMULATOR\n");
        }

        @Override
        public void section(String id, String title) {
            text.append("\n").append(title.toUpperCase()).append("\n");
        }

        @Override
        public void heading(String title) {
            text.append("\n").append(title).append("\n");
        }

        @Override
        public void paragraph(String value) {
            text.append(plain(value)).append("\n\n");
        }

        @Override
        public void bullets(String... items) {
            for (String item : items) {
                text.append("  - ").append(plain(item)).append("\n");
            }
            text.append("\n");
        }

        @Override
        public void steps(String... items) {
            for (int i = 0; i < items.length; i++) {
                text.append("  ").append(i + 1).append(". ").append(plain(items[i])).append("\n");
            }
            text.append("\n");
        }

        @Override
        public void netlist(String value) {
            code(value);
        }

        @Override
        public void code(String value) {
            for (String line : value.split("\n", -1)) {
                text.append("    ").append(line).append("\n");
            }
            text.append("\n");
        }

        @Override
        public void table(String[] headers, String[]... rows) {
            for (String[] row : rows) {
                text.append("  ").append(plain(String.join("  |  ", row))).append("\n");
            }
            text.append("\n");
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
