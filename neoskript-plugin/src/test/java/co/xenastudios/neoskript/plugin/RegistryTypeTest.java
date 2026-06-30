package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.api.type.Type;
import co.xenastudios.neoskript.core.runtime.Renderer;
import co.xenastudios.neoskript.core.type.TypeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the registry-backed types (biome, enchantment, attribute) parse against the live Bukkit
 * registries (available under MockBukkit, unlike the server-less lang unit tests).
 */
class RegistryTypeTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(NeoSkriptPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registryTypesParse() {
        TypeRegistry types = Renderer.typeRegistry();
        assertNotNull(types);

        Type<?> biome = types.byCodeName("biome");
        assertNotNull(biome, "biome type registered");
        Object plains = biome.parse("plains").orElse(null);
        assertNotNull(plains, "biome 'plains' should parse");

        Type<?> enchantment = types.byCodeName("enchantment");
        assertNotNull(enchantment, "enchantment type registered");
        assertTrue(enchantment.parse("sharpness").isPresent(), "enchantment 'sharpness' should parse");

        Type<?> profession = types.byCodeName("villagerprofession");
        assertNotNull(profession, "villagerprofession type registered");
        assertTrue(profession.parse("farmer").isPresent(), "villager profession 'farmer' should parse");

        Type<?> villagerType = types.byCodeName("villagertype");
        assertNotNull(villagerType, "villagertype type registered");
        assertTrue(villagerType.parse("plains").isPresent(), "villager type 'plains' should parse");

        // Material is a registry-backed enum (needs a server), so it is verified here, not in lang.
        Type<?> material = types.byCodeName("material");
        assertNotNull(material, "material type registered");
        assertTrue(material.parse("diamond").isPresent(), "material 'diamond' should parse");

        // InventoryType is registry-backed too (server-only).
        Type<?> inventoryType = types.byCodeName("inventorytype");
        assertNotNull(inventoryType, "inventorytype registered");
        assertTrue(inventoryType.parse("chest").isPresent(), "inventory type 'chest' should parse");

        // an unknown key parses to empty rather than throwing
        assertTrue(biome.parse("not_a_real_biome").isEmpty());
    }
}
