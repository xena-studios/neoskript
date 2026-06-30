package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.type.Type;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.Renderer;
import co.xenastudios.neoskript.core.type.TypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that each registered type is wired into the registry and round-trips a value (parse →
 * display → parse). Enum-backed types are checked generically against their own constants, so the
 * test is not fragile to per-version constant renames. This is the conformance gate that lets a type
 * entry flip to {@code done} in the inventory.
 */
class TypeCoverageTest {

    private static TypeRegistry registry() {
        BuiltinModule.registerAll(new DefaultSyntaxRegistry());
        TypeRegistry types = Renderer.typeRegistry();
        assertNotNull(types, "BuiltinModule.registerAll must install the type registry");
        return types;
    }

    /** Enum-backed types: each must parse the display form of its own first constant back to it. */
    @ParameterizedTest
    @ValueSource(strings = {
            "gamemode", "entitytype", "damagecause", "clicktype", "inventoryaction", "weathertype",
            "difficulty", "blockface", "equipmentslot", "spawnreason", "environment", "entityeffect",
            "healreason", "teleportcause", "soundcategory", "fireworktype",
    })
    @SuppressWarnings({"unchecked", "rawtypes"})
    void enumTypeRoundTrips(String codeName) {
        TypeRegistry types = registry();
        Type<?> type = types.byCodeName(codeName);
        assertNotNull(type, "type not registered: " + codeName);
        Object[] constants = type.typeClass().getEnumConstants();
        assertNotNull(constants, codeName + " should be enum-backed");
        assertTrue(constants.length > 0, codeName + " has no constants");
        Object sample = constants[0];
        String display = ((Type) type).toDisplayString(sample);
        assertEquals(sample, type.parse(display).orElseThrow(),
                codeName + " must round-trip its display form");
    }

    /** Non-enum types with explicit samples. */
    @Test
    void scalarTypesRoundTrip() {
        TypeRegistry types = registry();
        Map<String, String> samples = Map.of(
                "color", "red",
                "date", "2026/06/30 12:00:00");
        samples.forEach((codeName, sample) -> {
            Type<?> type = types.byCodeName(codeName);
            assertNotNull(type, "type not registered: " + codeName);
            assertTrue(type.parse(sample).isPresent(), codeName + " should parse '" + sample + "'");
        });
    }
}
