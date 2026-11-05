// Runs the built browser engine in Node. Usage, after "mvn package":
//   node scripts/browser-smoke-test.js
const assert = require("assert");
const fs = require("fs");
const path = require("path");

const file = path.resolve(__dirname, "../target/generated/js/simulator.js");
if (!fs.existsSync(file)) {
    console.error("Not found: " + file + "\nRun mvn package first.");
    process.exit(1);
}
const engine = require(file);

const netlist = "SIM_TIME 40\nWIRE A\nWIRE B\nWIRE OUT\nAND gate 5 A B OUT\n"
    + "STIMULUS 0 A 1\nSTIMULUS 0 B 1\nASSERT 5 OUT 1\n";
const other = "WIRE A\nWIRE B\nWIRE OUT\nOR gate 5 A B OUT\n";

function call(name, ...args) {
    assert.strictEqual(typeof engine[name], "function", name + " was not exported");
    const response = JSON.parse(engine[name](...args));
    assert.strictEqual(response.success, true, name + " failed: " + response.error);
    console.log("ok " + name);
    return response;
}

const run = call("runSimulation", netlist);
assert.ok(run.eventsProcessed > 0);
assert.ok(run.waveform.includes("$var wire 1"));

assert.strictEqual(call("validateCircuit", netlist).valid, true);
assert.strictEqual(call("analyzeCircuit", netlist).criticalPathDelay, 5);

const ports = call("detectWires", netlist);
assert.deepStrictEqual(ports.inputs, ["A", "B"]);
assert.deepStrictEqual(ports.outputs, ["OUT"]);

const table = call("generateTruthTable", netlist, "A,B", "OUT");
assert.deepStrictEqual(table.rows.map(row => row.outputs[0]), ["0", "0", "0", "1"]);

assert.strictEqual(call("checkEquivalent", netlist, netlist, "A,B", "OUT").equivalent, true);
const different = call("checkEquivalent", netlist, other, "A,B", "OUT");
assert.strictEqual(different.equivalent, false);
assert.strictEqual(different.counterexampleRow, 1);

const schematic = call("schematicLayout", netlist);
assert.ok(schematic.nodes.some(node => node.kind === "AND"));
assert.ok(schematic.nets.length > 0);

assert.ok(engine.userGuide().includes("Writing a netlist"));
console.log("ok userGuide");
const examples = call("exampleCircuits").examples;
assert.ok(examples.length > 0);
examples.forEach(example => assert.strictEqual(JSON.parse(engine.runSimulation(example.netlist)).success, true, example.name));
console.log("ok every example simulates");

const stateTable = call("generateTruthTable", examples.find(example => example.name === "2-bit counter").netlist, "", "");
assert.strictEqual(stateTable.rows.length, 4);

const session = call("debugStart", netlist);
assert.strictEqual(session.time, 0);
const step = call("debugStep");
assert.strictEqual(step.stepped, true);

const failure = JSON.parse(engine.runSimulation("WIRE A\nFOO x 1 A\n"));
assert.strictEqual(failure.success, false);
assert.ok(failure.error.includes("line 2"), failure.error);
console.log("ok errors");

console.log("Browser engine works.");
