package co.xenastudios.neoskript.plugin;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared assertion for bulk-syntax tests: every structure in the loaded scripts must parse.
 *
 * <p>NeoSkript isolates a parse error to its own structure (the rest of the file still loads), so a
 * bad line no longer stops an {@code on join} marker from firing. These tests therefore assert on the
 * reload summary — {@code "... (N failed)"} is printed only when at least one structure failed — which
 * detects any regression in the exercised syntax.
 */
final class ReloadAssert {

    private ReloadAssert() {
    }

    /** Reloads scripts as console and asserts the reload summary reports zero failed structures. */
    static void assertReloadHasNoFailures(ServerMock server) {
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) server.getConsoleSender();
        String message;
        boolean sawSummary = false;
        while ((message = console.nextMessage()) != null) {
            if (message.contains("NeoSkript reloaded")) {
                sawSummary = true;
                assertFalse(message.contains("failed"),
                        "reload reported parse failures — a listed syntax did not parse: " + message);
            }
        }
        assertTrue(sawSummary, "expected a reload summary message from /neoskript reload");
    }
}
