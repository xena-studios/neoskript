package co.xenastudios.neoskript.plugin;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour tests for anvil repair cost (settable) and named items. */
class AnvilNamedTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void anvilAndNamed() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("an.sk"), """
                command /anvil:
                    trigger:
                        set repair cost of (current inventory of player) to 7
                        send "COST:%repair cost of (current inventory of player)%" to player
                command /named:
                    trigger:
                        give (diamond named "Shiny") to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        p.openInventory(server.createInventory(null, InventoryType.ANVIL));

        server.dispatchCommand(p, "anvil");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);
        assertTrue(seen.contains("COST:7"), "anvil repair cost set to 7 reads back 7; got " + seen);

        server.dispatchCommand(p, "named");
        ItemStack named = null;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                named = item;
                break;
            }
        }
        assertTrue(named != null && named.getItemMeta().hasDisplayName(), "the given diamond has a display name");
        assertEquals("Shiny", PlainTextComponentSerializer.plainText().serialize(named.getItemMeta().displayName()),
                "the diamond is named Shiny");
    }
}
