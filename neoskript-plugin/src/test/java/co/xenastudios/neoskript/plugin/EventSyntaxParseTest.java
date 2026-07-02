package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parse-verification for the event handlers and event-value syntax that can't be behaviour-tested in
 * MockBukkit (they only fire inside live Bukkit events): beacon toggle, enderman enrage, bucket catch,
 * damage cause, resource-pack state, applied enchantments, and chat format. Asserts the whole script
 * loads with no parse failures.
 */
class EventSyntaxParseTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void allEventSyntaxParses() throws IOException {
        List<String> logs = new ArrayList<>();
        plugin.getLogger().addHandler(new Handler() {
            public void publish(LogRecord r) { logs.add(r.getMessage()); }
            public void flush() { }
            public void close() { }
        });
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("events.sk"), """
                on beacon toggle:
                    broadcast "beacon toggled"
                on enderman enrage:
                    broadcast "enderman angry"
                on bucket catch:
                    broadcast "caught"
                on damage:
                    if the damage was caused by fall:
                        broadcast "fell"
                    if the damage wasn't caused by fire:
                        broadcast "not fire"
                on resource pack response:
                    if the resource pack was accepted:
                        broadcast "rp accepted"
                on enchant:
                    broadcast "applied %applied enchantments%"
                on chat:
                    set the chat format to "&7[player]: [message]"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        boolean anyFailure = logs.stream().anyMatch(m -> m != null && m.contains("Failed to parse"));
        assertTrue(!anyFailure, "all event syntax parses without error; logs: " + logs);
    }
}
