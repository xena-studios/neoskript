// The VS Code extension: Skript's grammar and editing rules come from package.json; this starts NeoSkript's
// language server (server/server.cjs, built from editors/language-server) for every .sk file.
import * as path from "node:path";
import { commands, workspace, type ExtensionContext } from "vscode";
import { LanguageClient, TransportKind, type LanguageClientOptions, type ServerOptions } from "vscode-languageclient/node";

let client: LanguageClient | undefined;

export async function activate(context: ExtensionContext) {
  const module = context.asAbsolutePath(path.join("server", "server.cjs"));
  const server: ServerOptions = {
    run: { module, transport: TransportKind.ipc },
    debug: { module, transport: TransportKind.ipc, options: { execArgv: ["--nolazy", "--inspect=6009"] } },
  };
  const options: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", language: "skript" }, { scheme: "untitled", language: "skript" }],
    synchronize: { fileEvents: workspace.createFileSystemWatcher("**/*.sk") },
  };
  client = new LanguageClient("neoskript", "NeoSkript", server, options);
  context.subscriptions.push(commands.registerCommand("neoskript.restart", () => client?.restart()));
  await client.start();
}

export async function deactivate() {
  await client?.stop();
}
