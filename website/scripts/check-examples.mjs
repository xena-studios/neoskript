// Loads every ```skript example in the guides with NeoSkript's script checker,
// so the docs only show syntax NeoSkript has. Each example is checked on its
// own (examples on different pages can share a command or function name).
//
//   pnpm docs:examples                       check every page
//   pnpm docs:examples scripts/commands      only pages whose path has this in it
//
// It needs Java 21 and the CLI, built with `./gradlew :neoskript-cli:shadowJar`
// (or set NEOSKRIPT_CLI to a jar). An example that isn't a whole script, one
// whose first line is indented code rather than a structure, is checked as the
// trigger of a command. Blocks meant to fail, such as an error being explained,
// say so in their meta: ```skript nocheck.
import { execFile } from "node:child_process";
import { mkdtempSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const docs = fileURLToPath(new URL("../src/content/docs/docs", import.meta.url));
const root = fileURLToPath(new URL("../..", import.meta.url));
const filter = process.argv[2] ?? "";

function findCli() {
  if (process.env.NEOSKRIPT_CLI) return process.env.NEOSKRIPT_CLI;
  const libs = join(root, "neoskript-cli/build/libs");
  const jar = readdirSync(libs, { withFileTypes: true }).find((f) => f.name.endsWith(".jar"));
  if (!jar) throw new Error("No CLI jar: run ./gradlew :neoskript-cli:shadowJar");
  return join(libs, jar.name);
}

function pages(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((f) =>
    f.isDirectory() ? pages(join(dir, f.name)) : /\.mdx?$/.test(f.name) ? [join(dir, f.name)] : [],
  );
}

// A script starts with a structure: an event, command, function, options and so on.
const STRUCTURE = /^(on |every |at |command |function |local function |options:|variables:|aliases:|auto reload|using |example:|#)/i;

function examples(file) {
  const text = readFileSync(file, "utf8");
  const out = [];
  const re = /^([ \t]*)```(?:skript|sk)([^\n]*)\n([\s\S]*?)^\1```/gm;
  for (const [, indent, meta, body] of text.matchAll(re)) {
    if (/\bnocheck\b/.test(meta)) continue;
    let code = body
      .split("\n")
      .map((line) => (line.startsWith(indent) ? line.slice(indent.length) : line))
      .join("\n");
    const first = code.split("\n").find((line) => line.trim() !== "" && !line.trim().startsWith("#")) ?? "";
    if (!STRUCTURE.test(first)) {
      code = `command /docs-example:\n\ttrigger:\n${code.split("\n").map((line) => (line ? `\t\t${line}` : line)).join("\n")}`;
    }
    const line = text.slice(0, text.indexOf(body)).split("\n").length;
    out.push({ line, code });
  }
  return out;
}

function check(cli, dir) {
  return new Promise((resolve) => {
    execFile("java", ["-jar", cli, "check", dir], { maxBuffer: 1 << 24 }, (error, stdout, stderr) =>
      resolve({ code: error ? error.code : 0, output: stdout + stderr }),
    );
  });
}

const cli = findCli();
const work = mkdtempSync(join(tmpdir(), "neoskript-docs-"));
const jobs = [];
for (const file of pages(docs)) {
  const page = relative(docs, file).replace(/\.mdx?$/, "");
  if (!page.includes(filter)) continue;
  for (const { line, code } of examples(file)) {
    const dir = join(work, `${page.replaceAll("/", "_")}-${line}`);
    mkdirSync(dir);
    writeFileSync(join(dir, "example.sk"), code);
    jobs.push({ page, line, dir });
  }
}

console.log(`Checking ${jobs.length} examples with ${relative(root, cli)}...`);
let failed = 0;
const queue = [...jobs];
await Promise.all(
  Array.from({ length: 4 }, async () => {
    for (let job = queue.shift(); job; job = queue.shift()) {
      const { code, output } = await check(cli, job.dir);
      // Warnings are fine in an example only if it says so; errors never are.
      const problems = output.split("\n").filter((l) => /^\s+(error|warning)\s+line/.test(l));
      if (code !== 0 || problems.length > 0) {
        failed++;
        console.log(`\n${job.page}.mdx, the example at line ${job.line}:`);
        console.log(problems.length > 0 ? problems.join("\n") : output.trim());
      }
    }
  }),
);
console.log(failed === 0 ? `All ${jobs.length} examples load.` : `\n${failed} of ${jobs.length} examples don't load cleanly.`);
process.exit(failed === 0 ? 0 : 1);
