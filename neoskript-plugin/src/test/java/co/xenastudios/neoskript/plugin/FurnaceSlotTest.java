package co.xenastudios.neoskript.plugin;

import org.bukkit.Material;
import org.bukkit.block.Furnace;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Behaviour test for the furnace ore/input slot expression (settable). */
class FurnaceSlotTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void oreSlot() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("fs.sk"), """
                command /fs:
                    trigger:
                        set block at (location of player) to furnace
                        set {_b::*} to blocks between (location of player) and (location of player)
                        set ore slot of {_b::1} to diamond
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "fs");

        var state = p.getLocation().getBlock().getState();
        assertEquals(Material.FURNACE, p.getLocation().getBlock().getType(), "block became a furnace");
        Furnace furnace = (Furnace) state;
        var input = furnace.getInventory().getItem(0);
        assertEquals(Material.DIAMOND, input == null ? Material.AIR : input.getType(),
                "the ore/input slot holds the diamond");
    }
}
