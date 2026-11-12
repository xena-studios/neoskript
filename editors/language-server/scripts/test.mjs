// Starts the built server (dist/server.cjs) as an editor would, over stdio, in a workspace of two scripts, and
// checks what it answers: problems (with another script's function known), hover, completion, the outline and go
// to definition. `pnpm test`, after `pnpm build`.
import { spawn } from "node:child_process";
import { mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import assert from "node:assert/strict";

const dir = await mkdtemp(join(tmpdir(), "neoskript-ls-"));
const main = `on join:
\tsend "Welcome, %player%!" to player
\twelcome(player)
\tsend {@prefix}

options:
\tprefix: &6[Server]

command /hi:
\ttrigger:
\t\tfrobnicate the player
`;
const lib = "function welcome(p: player):\n\tsend \"hi\" to {_p}\n";
await writeFile(join(dir, "main.sk"), main);
await writeFile(join(dir, "lib.sk"), lib);
const mainUri = pathToFileURL(join(dir, "main.sk")).toString();
const libUri = pathToFileURL(join(dir, "lib.sk")).toString();

const server = spawn(process.execPath, [new URL("../dist/server.cjs", import.meta.url).pathname, "--stdio"], { stdio: ["pipe", "pipe", "inherit"] });
let buffer = Buffer.alloc(0);
let nextId = 1;
const waiting = new Map();
const diagnostics = [];
server.stdout.on("data", (chunk) => {
  buffer = Buffer.concat([buffer, chunk]);
  for (;;) {
    const header = buffer.indexOf("\r\n\r\n");
    if (header < 0) return;
    const length = Number(/Content-Length: (\d+)/i.exec(buffer.subarray(0, header).toString())[1]);
    if (buffer.length < header + 4 + length) return;
    const message = JSON.parse(buffer.subarray(header + 4, header + 4 + length).toString());
    buffer = buffer.subarray(header + 4 + length);
    if (message.id !== undefined && waiting.has(message.id)) {
      waiting.get(message.id)(message.result);
      waiting.delete(message.id);
    } else if (message.method === "textDocument/publishDiagnostics") {
      diagnostics.push(message.params);
    } else if (message.id !== undefined && message.method) {
      send({ jsonrpc: "2.0", id: message.id, result: null });
    }
  }
});
function send(message) {
  const body = JSON.stringify(message);
  server.stdin.write(`Content-Length: ${Buffer.byteLength(body)}\r\n\r\n${body}`);
}
const request = (method, params) => new Promise((resolve) => {
  const id = nextId++;
  waiting.set(id, resolve);
  send({ jsonrpc: "2.0", id, method, params });
});
const notify = (method, params) => send({ jsonrpc: "2.0", method, params });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const init = await request("initialize", { processId: process.pid, rootUri: pathToFileURL(dir).toString(), workspaceFolders: [{ uri: pathToFileURL(dir).toString(), name: "test" }], capabilities: {} });
assert.ok(init.capabilities.hoverProvider);
notify("initialized", {});
await sleep(300);
notify("textDocument/didOpen", { textDocument: { uri: mainUri, languageId: "skript", version: 1, text: main } });
await sleep(800);

// Problems: the typo is an error; welcome() is known from lib.sk.
const latest = diagnostics.filter((d) => d.uri === mainUri).at(-1);
assert.ok(latest, "no diagnostics published");
const errors = latest.diagnostics.map((d) => `${d.range.start.line + 1}: ${d.message}`);
assert.equal(errors.length, 1, errors.join("\n"));
assert.match(errors[0], /^11: .*frobnicate/);

// Hover on the send line shows the Message effect.
const hover = await request("textDocument/hover", { textDocument: { uri: mainUri }, position: { line: 1, character: 2 } });
assert.match(hover.contents.value, /\*\*Message\*\*/);
// Hover on the function call shows its signature.
const call = await request("textDocument/hover", { textDocument: { uri: mainUri }, position: { line: 2, character: 3 } });
assert.match(call.contents.value, /welcome\(p: player\)/);

// Completion on an indented line offers effects and functions; at the top, structures.
const inside = await request("textDocument/completion", { textDocument: { uri: mainUri }, position: { line: 1, character: 1 } });
assert.ok(inside.some((c) => c.label.startsWith("message")), "no message effect");
assert.ok(inside.some((c) => c.label === "welcome"), "no welcome function");
const top = await request("textDocument/completion", { textDocument: { uri: mainUri }, position: { line: 4, character: 0 } });
assert.ok(top.some((c) => c.label === "command"));

// The outline.
const symbols = await request("textDocument/documentSymbol", { textDocument: { uri: mainUri } });
assert.deepEqual(symbols.map((s) => s.name), ["on join", "options", "command /hi"]);

// Go to definition: the function in lib.sk, and the option.
const definition = await request("textDocument/definition", { textDocument: { uri: mainUri }, position: { line: 2, character: 3 } });
assert.equal(definition[0].uri, libUri);
const option = await request("textDocument/definition", { textDocument: { uri: mainUri }, position: { line: 3, character: 9 } });
assert.equal(option[0].range.start.line, 6);

await request("shutdown", null);
notify("exit", null);
console.log("language server: all checks passed");
