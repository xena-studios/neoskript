package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.variable.FlatFileVariableStore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PersistenceTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(NeoSkriptPlugin.class); // registers the built-in serializers
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void persistsLocationsAndItems(@TempDir Path dir) throws IOException {
        PlayerMock player = server.addPlayer();
        Path file = dir.resolve("variables.csv");

        Map<String, Object> in = new LinkedHashMap<>();
        in.put("home", player.getLocation());
        in.put("reward", new ItemStack(Material.DIAMOND, 5));
        FlatFileVariableStore.save(file, in);

        Map<String, Object> out = FlatFileVariableStore.load(file);

        Location home = assertInstanceOf(Location.class, out.get("home"));
        assertEquals(player.getLocation().getX(), home.getX());
        assertEquals(player.getLocation().getBlockY(), home.getBlockY());

        ItemStack reward = assertInstanceOf(ItemStack.class, out.get("reward"));
        assertEquals(Material.DIAMOND, reward.getType());
        assertEquals(5, reward.getAmount());
    }
}
