package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour tests for date conditions: in the past/future, and 'was more/less than X ago'. */
class DateConditionTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void dateConditions() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("dt.sk"), """
                command /dt:
                    trigger:
                        set {_past} to 1 hour ago
                        set {_future} to 1 hour later
                        if {_past} is in the past:
                            send "PAST-OK" to player
                        if {_future} is in the future:
                            send "FUTURE-OK" to player
                        if {_past} was more than 1 minute ago:
                            send "MORE-OK" to player
                        if {_past} was less than 1 minute ago:
                            send "LESS-BAD" to player
                        if {_future} is in the past:
                            send "FUTURE-PAST-BAD" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "dt");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("PAST-OK"), "1 hour ago is in the past; got " + seen);
        assertTrue(seen.contains("FUTURE-OK"), "1 hour later is in the future");
        assertTrue(seen.contains("MORE-OK"), "1 hour ago was more than 1 minute ago");
        assertFalse(seen.contains("LESS-BAD"), "1 hour ago was NOT less than 1 minute ago");
        assertFalse(seen.contains("FUTURE-PAST-BAD"), "a future date is not in the past");
    }
}
