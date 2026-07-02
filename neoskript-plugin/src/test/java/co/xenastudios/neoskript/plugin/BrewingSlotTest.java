package co.xenastudios.neoskript.plugin;

import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Behaviour test for the brewing stand's first bottle slot (settable). */
class BrewingSlotTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void bottleSlot() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("bw.sk"), """
                command /bw:
                    trigger:
                        set block at (location of player) to brewing stand
                        set {_b::*} to blocks between (location of player) and (location of player)
                        set first bottle slot of {_b::1} to glass bottle
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "bw");

        assertEquals(Material.BREWING_STAND, p.getLocation().getBlock().getType(), "block became a brewing stand");
        BrewingStand stand = (BrewingStand) p.getLocation().getBlock().getState();
        var bottle = stand.getInventory().getItem(0);
        assertEquals(Material.GLASS_BOTTLE, bottle == null ? Material.AIR : bottle.getType(),
                "the first bottle slot holds the glass bottle");
    }
}
