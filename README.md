# DigitalCircuitSimulator

An object-oriented Java digital-circuit simulator with discrete-event timing,
logic gates, sequential components, fault injection, topology validation, and
Value Change Dump (VCD) waveform export.

## Requirements

- JDK 21 or newer
- PowerShell on Windows, or equivalent shell commands
- JUnit is bundled in `lib/junit-platform-console-standalone.jar`

## Compile and test with Maven

Maven is the canonical build tool for this project:

```powershell
mvn test
```

This compiles `src`, compiles every test under `test`, and runs the JUnit 5
suite. Java 21 or newer is required.

## Manual compile

The following commands are useful when Maven is unavailable:

```powershell
$out = ".build\classes"
New-Item -ItemType Directory -Force $out
$sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName
javac -d $out $sources
```

## Run tests manually

```powershell
$out = ".build\classes"
$sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName
$tests = Get-ChildItem test -Recurse -Filter *.java | ForEach-Object FullName
javac -d $out $sources
javac -cp "$out;lib\junit-platform-console-standalone.jar" -d $out $tests
java -jar lib\junit-platform-console-standalone.jar execute `
  --class-path $out --scan-class-path --include-classname '.*Test'
```

## Run a simulation

```text
java simulator.Main <input.netlist> [output.vcd]
```

The output defaults to `output.vcd` when no second argument is provided.

## Run the desktop dashboard

```text
java simulator.gui.SimulatorDashboard
```

The dashboard edits netlists, runs and steps simulations, shows waveforms, and
generates truth tables. Truth tables and equivalence checks work on
combinational circuits. Leave the wire fields empty or press **Detect wires**
to use the netlist's undriven wires as inputs and its unread wires as outputs.

## Netlist syntax

```text
SIM_TIME 100
WIRE A
WIRE B
WIRE OUT
STIMULUS 0 A 1
STIMULUS 0 B 0
AND and1 10 A B OUT
```

Supported components are `AND`, `OR`, `NOT`, `NAND`, `NOR`, `XOR`,
`BUFFER`, `DFF`, and `CLOCK`.

Additional commands:

```text
FAULT STUCK_AT_0 A
FAULT STUCK_AT_1 B
CLOCK master 20 CLOCK_WIRE
DFF register 5 DATA CLOCK_WIRE Q
```

Stimulus values are `0`, `1`, or `Z`. Wires must be declared before they are
referenced. Macro definitions use `DEF ...` and `ENDDEF`.

Assertions can verify a wire at a simulation timestamp:

```text
ASSERT 20 SUM 1
ASSERT 20 BUS X
```

`X` means an unknown or contended signal. An assertion failure stops the run
with the failing timestamp, wire, expected value, and actual value.

## CLI analysis commands

```powershell
java simulator.Main simulate circuit.netlist output.vcd
java simulator.Main validate circuit.netlist
java simulator.Main analyze circuit.netlist
java simulator.Main truth-table circuit.netlist A,B SUM
java simulator.Main equivalent circuit-a.netlist circuit-b.netlist A,B SUM
```

The simulation engine also exposes a `SimulationDebugger` API for stepping one
event at a time, inspecting simulation time and pending-event count, and then
resuming execution. This API is used by future GUI debugger controls without
coupling the UI to the event queue.

`truth-table` enumerates all binary combinations for the named input wires and
prints their output states. `equivalent` compares two combinational netlists
and returns a counterexample row when their outputs differ.

The Swing dashboard now exposes the same controls through **Step Event** and
**Reset**. Stepping updates the current simulation time, pending event count,
and waveform viewer without restarting the session.

The dashboard's **Analysis** tab also provides timing analysis, truth-table
generation, and equivalence checking. Input and output wires are entered as
comma-separated names; equivalence checking accepts a second netlist in the
comparison editor.

## Shared application API

CLI, GUI, and WebAssembly clients share the operations in
`SimulationApplicationService`: simulation, validation, timing analysis,
truth-table generation, and equivalence checking. WebAssembly exports the
following JSON APIs:

```text
runSimulation(netlist)
validateCircuit(netlist)
analyzeCircuit(netlist)
generateTruthTable(netlist, "A,B", "SUM")
checkEquivalent(firstNetlist, secondNetlist, "A,B", "SUM")
```

## Browser demo

Build the TeaVM client and serve the repository root over HTTP:

```powershell
mvn package
python -m http.server 8000
```

Open `http://localhost:8000/`. The tracked [index.html](D:/Hp/Documents/Projects/DigitalCircuitSimulator/index.html)
loads `target/generated/js/simulator.js` and exercises simulation, validation,
timing analysis, truth-table generation, and equivalence checking. Serving the
files over HTTP is required because browsers block some generated-script
behaviors when the page is opened directly from `file://`.

## Outputs

The simulator validates combinational topology, runs a timestamp-ordered event
queue, prints simulation telemetry, and writes a VCD waveform. VCD identifiers
are generated separately from wire names so names containing spaces or special
characters do not become invalid identifiers.

## Project layout

- `src/simulator/core` — wires, pins, components, and signal observation
- `src/simulator/components` — gates, clocks, sequential logic, and modules
- `src/simulator/engine` — event processing and static timing analysis
- `src/simulator/parser` — netlist parsing, factories, and validation
- `src/simulator/io` — VCD export
- `src/simulator/fault` — stuck-at fault injection
- `test` — JUnit unit and integration-style tests

Generated `.class` files should not be treated as source. Build output should
be placed in a temporary or ignored directory such as `.build`.

## Object-oriented design

The implementation intentionally uses established object-oriented patterns:

- **Observer** — `Wire` notifies `InputPin` and waveform observers when signals
  change.
- **Composite** — `Module` treats nested components as a reusable circuit
  component and bridges its public pins to internal pins and wires.
- **State** — `SequentialComponent` delegates memory behavior to
  `MemoryState` implementations such as `LowState`, `HighState`, and
  `UnknownState`.
- **Factory/Registry** — `ComponentFactory` maps netlist type names to
  registered `ComponentCreator` functions. New component types can be
  registered without changing the creation switch.
- **Facade** — `SimulationFacade` and `SimulationRunner` expose a small
  application boundary for CLI, GUI, and WebAssembly clients.
- **Dependency Injection** — `SimulationContext` owns the event engine for one
  run. `SimulationRunner` creates a fresh context per invocation, while
  components, parsers, and VCD loggers receive that context instead of looking
  up global state.
- **Value Object** — `TimingAnalysisResult` and `SimulationResult` carry
  structured analysis and execution results instead of requiring callers to
  parse console output.
- **Strategy seam** — wire driver resolution and timing analysis are isolated
  behind focused services so their policies can evolve independently.

New functionality should preserve these boundaries. Prefer explicit context
dependencies and small interfaces over direct global lookups, and add tests
for each public object contract. Legacy constructors remain available and use
the default context for compatibility; new application code should create a
`SimulationContext` per simulation session.
