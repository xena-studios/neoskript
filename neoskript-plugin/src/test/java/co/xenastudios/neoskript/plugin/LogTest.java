package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour test for the log effect (to a file under the plugin's logs directory). */
class LogTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void logToFile() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("lg.sk"), """
                command /lg:
                    trigger:
                        log "hello from neoskript" to file "audit"
                        log "second line" to file "audit"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "lg");

        Path logFile = plugin.getDataFolder().toPath().resolve("logs").resolve("audit.log");
        assertTrue(Files.exists(logFile), "the log file should be created under logs/");
        String content = Files.readString(logFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("hello from neoskript"), "first message written; got: " + content);
        assertTrue(content.contains("second line"), "appended message written too");
    }
}
