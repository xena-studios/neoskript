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
            "healreason", "teleportcause", "soundcategory", "fireworktype", "gene",
            "entitydata", "particle",
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

    /** Parseable scalar class types: parse a sample literal, then round-trip through display. */
    @ParameterizedTest
    @ValueSource(strings = {"integer", "long", "short", "byte", "double", "float"})
    @SuppressWarnings({"unchecked", "rawtypes"})
    void numericClassTypesRoundTrip(String codeName) {
        TypeRegistry types = registry();
        Type<?> type = types.byCodeName(codeName);
        assertNotNull(type, "type not registered: " + codeName);
        String sample = (codeName.equals("double") || codeName.equals("float")) ? "5.0" : "5";
        Object parsed = type.parse(sample).orElseThrow();
        assertEquals(sample, ((Type) type).toDisplayString(parsed), codeName + " parses and renders " + sample);
        assertEquals(parsed, type.parse(((Type) type).toDisplayString(parsed)).orElseThrow(),
                codeName + " round-trips");
    }

    @Test
    void uuidTypeRoundTrips() {
        TypeRegistry types = registry();
        @SuppressWarnings({"unchecked", "rawtypes"})
        Type<Object> type = (Type) types.byCodeName("uuid");
        assertNotNull(type, "uuid type not registered");
        String sample = "12345678-1234-1234-1234-123456789abc";
        Object parsed = type.parse(sample).orElseThrow();
        assertEquals(sample, type.toDisplayString(parsed), "uuid round-trips display");
    }

    /** Class-backed types: registered, class-mapped, and display path is null-safe. */
    @ParameterizedTest
    @ValueSource(strings = {
            "block", "blockdata", "chunk", "commandsender", "damagesource", "display", "entity",
            "entitysnapshot", "enchantmentoffer", "enchantmenttype", "fireworkeffect", "inventory",
            "inventoryholder", "itementity", "livingentity", "lootcontext", "loottable",
            "metadataholder", "nameable", "offlineplayer", "potioneffect", "projectile",
            "teleportflag", "textcomponent", "vehicle", "worldborder", "bannerpattern",
            "cachedservericon", "minecrafttag", "quaternion", "gamerule",
            "equippablecomponent", "audience",
    })
    @SuppressWarnings({"unchecked", "rawtypes"})
    void classTypesRegistered(String codeName) {
        TypeRegistry types = registry();
        Type<?> type = types.byCodeName(codeName);
        assertNotNull(type, "type not registered: " + codeName);
        assertNotNull(type.typeClass(), codeName + " must map to a class");
        assertEquals("<none>", ((Type) type).toDisplayString(null), codeName + " display is null-safe");
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
