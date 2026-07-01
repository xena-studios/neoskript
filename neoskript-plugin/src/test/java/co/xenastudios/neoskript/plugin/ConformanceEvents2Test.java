package co.xenastudios.neoskript.plugin;

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

/** Conformance: the broader set of events NeoSkript registers all resolve (single file + join marker). */
class ConformanceEvents2Test {

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
    void moreImplementedEventsRegister() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("conf_events2.sk"), """
                on join:
                    send "JOINED2" to player
                on bed enter:
                    set {_x} to 1
                on bucket empty:
                    set {_x} to 1
                on bucket fill:
                    set {_x} to 1
                on chunk load:
                    set {_x} to 1
                on chunk unload:
                    set {_x} to 1
                on click:
                    set {_x} to 1
                on command:
                    set {_x} to 1
                on consume:
                    set {_x} to 1
                on craft:
                    set {_x} to 1
                on damage:
                    set {_x} to 1
                on death:
                    set {_x} to 1
                on drop:
                    set {_x} to 1
                on enchant:
                    set {_x} to 1
                on experience change:
                    set {_x} to 1
                on explode:
                    set {_x} to 1
                on flight toggle:
                    set {_x} to 1
                on gamemode change:
                    set {_x} to 1
                on grow:
                    set {_x} to 1
                on heal:
                    set {_x} to 1
                on inventory click:
                    set {_x} to 1
                on inventory close:
                    set {_x} to 1
                on inventory drag:
                    set {_x} to 1
                on inventory open:
                    set {_x} to 1
                on join:
                    set {_x} to 1
                on portal:
                    set {_x} to 1
                on projectile hit:
                    set {_x} to 1
                on quit:
                    set {_x} to 1
                on redstone:
                    set {_x} to 1
                on respawn:
                    set {_x} to 1
                on shoot:
                    set {_x} to 1
                on sign change:
                    set {_x} to 1
                on vehicle exit:
                    set {_x} to 1
                on weather change:
                    set {_x} to 1
                on world init:
                    set {_x} to 1
                on world load:
                    set {_x} to 1
                on world save:
                    set {_x} to 1
                """, StandardCharsets.UTF_8);
        ReloadAssert.assertReloadHasNoFailures(server);

        PlayerMock player = server.addPlayer();
        boolean joined = false;
        String m;
        while ((m = player.nextMessage()) != null) {
            if (m.contains("JOINED2")) {
                joined = true;
            }
        }
        assertTrue(joined, "all listed event names must resolve");
    }
}
