package co.xenastudios.neoskript.plugin;

import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies named event-value expressions parse/run, and behaviour-tests 'damage' by firing an event. */
class EventValuesTest {

    private ServerMock server;
    private NeoSkriptPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(NeoSkriptPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("deprecation")
    void eventValues() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("ev.sk"), """
                on damage:
                    if damage is 5:
                        send "DMG5" to victim

                command /ev:
                    trigger:
                        set {_a} to damage
                        set {_b} to final damage
                        set {_c} to damage cause
                        set {_d} to attacker
                        set {_e} to death message
                        set {_f} to join message
                        set {_g} to quit message
                        set {_h} to spawn reason
                        set {_i} to clicked block
                        set {_j} to dropped exp
                        send "DONE" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "ev");
        java.util.Set<String> seen = new java.util.HashSet<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            seen.add(m);
        }
        assertTrue(seen.contains("DONE"), "all event-value expressions parse and run (null outside events)");

        // behaviour: fire a damage event with damage 5 -> the 'damage' event value reads 5
        PlayerMock victim = server.addPlayer("Victim");
        EntityDamageEvent dmg = new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.CUSTOM, 5.0);
        server.getPluginManager().callEvent(dmg);
        boolean got = false;
        while ((m = victim.nextMessage()) != null) {
            if (m.contains("DMG5")) {
                got = true;
            }
        }
        assertTrue(got, "the 'damage' event value reads the fired event's damage");
    }
}
