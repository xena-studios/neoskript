/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class ServerProcessTest {

    @TempDir
    Path dir;

    private ServerProcess start(String script) throws Exception {
        return ServerProcess.start(List.of("sh", "-c", script), dir, false);
    }

    @Test
    void awaitsLinesAndSkipsOnesBeforeTheMark() throws Exception {
        try (ServerProcess server = start("echo first 1; echo first 2; sleep 0.2; echo second 3")) {
            assertEquals("1", server.await(Pattern.compile("first (\\d)"), Duration.ofSeconds(10)).group(1));
            server.await(Pattern.compile("second"), Duration.ofSeconds(10));
            int mark = server.mark();
            assertThrows(HarnessFailure.class, () -> server.await(Pattern.compile("first"), mark, Duration.ofSeconds(10)));
            assertEquals(0, server.awaitExit(Duration.ofSeconds(10)));
        }
        assertTrue(java.nio.file.Files.readString(dir.resolve("harness-console.log")).contains("second 3"));
    }

    @Test
    void failsFastWhenAPluginFailsToEnable() throws Exception {
        try (ServerProcess server = start("echo '[ERROR]: Error occurred while enabling Skript v2.17.0'; sleep 30")) {
            HarnessFailure failure = assertThrows(
                    HarnessFailure.class, () -> server.await(ServerProcess.DONE, Duration.ofSeconds(20)));
            assertTrue(failure.getMessage().contains("Skript v2.17.0"), failure.getMessage());
        }
    }

    @Test
    void timesOut() throws Exception {
        try (ServerProcess server = start("sleep 30")) {
            HarnessFailure failure = assertThrows(
                    HarnessFailure.class, () -> server.await(Pattern.compile("never"), Duration.ofMillis(200)));
            assertTrue(failure.getMessage().startsWith("Timed out"), failure.getMessage());
        }
    }

    @Test
    void sendsConsoleCommands() throws Exception {
        try (ServerProcess server = start("read line; echo \"got $line\"")) {
            server.command("skript reload scripts");
            server.await(Pattern.compile("got skript reload scripts"), Duration.ofSeconds(10));
        }
    }
}
