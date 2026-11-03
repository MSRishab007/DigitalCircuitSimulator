# DigitalCircuitSimulator

An object-oriented Java digital-circuit simulator with discrete-event timing,
logic gates, sequential components, fault injection, topology validation, and
Value Change Dump (VCD) waveform export.

## Requirements

- JDK 11 or newer
- PowerShell on Windows, or equivalent shell commands
- JUnit is bundled in `lib/junit-platform-console-standalone.jar`

## Compile

```powershell
$out = ".build\classes"
New-Item -ItemType Directory -Force $out
$sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName
javac -d $out $sources
```

## Run tests

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
