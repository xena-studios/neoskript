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

        // Newer registry types: verify by round-tripping a real key pulled from the live registry,
        // so the test does not depend on guessing version-specific key names.
        checkRegistry("damagetype", org.bukkit.Registry.DAMAGE_TYPE);
        checkRegistry("potiontype", org.bukkit.Registry.POTION);
        checkRegistry("structuretype", org.bukkit.Registry.STRUCTURE_TYPE);
        checkRegistry("musicinstrument", org.bukkit.Registry.INSTRUMENT);
        checkRegistry("cattype", org.bukkit.Registry.CAT_VARIANT);
        checkRegistry("frogvariant", org.bukkit.Registry.FROG_VARIANT);

        // an unknown key parses to empty rather than throwing
        assertTrue(biome.parse("not_a_real_biome").isEmpty());
    }

    private void checkRegistry(String codeName, org.bukkit.Registry<? extends org.bukkit.Keyed> registry) {
        Type<?> type = Renderer.typeRegistry().byCodeName(codeName);
        assertNotNull(type, codeName + " type registered");
        org.bukkit.Keyed first = registry.iterator().next();
        String keyPath = first.getKey().getKey();
        assertTrue(type.parse(keyPath).isPresent(), codeName + " should parse its own key '" + keyPath + "'");
    }
}
