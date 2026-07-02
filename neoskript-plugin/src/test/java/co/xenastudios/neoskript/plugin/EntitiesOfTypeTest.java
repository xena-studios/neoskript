package co.xenastudios.neoskript.plugin;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour test for 'entities of type %entitydatas%'. */
class EntitiesOfTypeTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void entitiesOfType() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("en.sk"), """
                command /en:
                    trigger:
                        send "ZOMBIES:%size of (entities of type zombie)%" to player
                        send "SKELETONS:%size of (entities of type skeleton)%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        p.getWorld().spawnEntity(p.getLocation(), EntityType.ZOMBIE);
        p.getWorld().spawnEntity(p.getLocation(), EntityType.ZOMBIE);

        server.dispatchCommand(p, "en");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("ZOMBIES:2"), "two zombies were spawned; got " + seen);
        assertTrue(seen.contains("SKELETONS:0"), "no skeletons spawned");
    }
}
