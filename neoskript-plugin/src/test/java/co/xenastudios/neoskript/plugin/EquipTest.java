package co.xenastudios.neoskript.plugin;

import org.bukkit.Material;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Behaviour test for the equip effect: items land in their matching equipment slots. */
class EquipTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void equip() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("eq.sk"), """
                command /eq:
                    trigger:
                        equip player with diamond helmet and diamond boots
                        equip player with iron chestplate
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "eq");

        assertEquals(Material.DIAMOND_HELMET, type(p.getInventory().getHelmet()), "helmet equipped to head");
        assertEquals(Material.DIAMOND_BOOTS, type(p.getInventory().getBoots()), "boots equipped to feet");
        assertEquals(Material.IRON_CHESTPLATE, type(p.getInventory().getChestplate()), "chestplate equipped to chest");
    }

    private static Material type(org.bukkit.inventory.ItemStack item) {
        return item == null ? Material.AIR : item.getType();
    }
}
