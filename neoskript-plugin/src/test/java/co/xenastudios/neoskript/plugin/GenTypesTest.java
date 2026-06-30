package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.api.type.Type;
import co.xenastudios.neoskript.core.runtime.Renderer;
import co.xenastudios.neoskript.core.type.TypeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bulk-resolved (GenTypes) enum/registry types parse a sample value under a live server. */
class GenTypesTest {

    private ServerMock server;

    @BeforeEach
    void setUp() { server = MockBukkit.mock(); MockBukkit.load(NeoSkriptPlugin.class); }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void resolvedTypesParseTheirSamples() {
        TypeRegistry types = Renderer.typeRegistry();
        Map<String, String> samples = Map.ofEntries(
            java.util.Map.entry("bannerpatterntype", "minecraft:base"),
            java.util.Map.entry("billboard", "fixed"),
            java.util.Map.entry("bukkitparticle", "flame"),
            java.util.Map.entry("inventoryclosereason", "player"),
            java.util.Map.entry("itemdisplaytransform", "none"),
            java.util.Map.entry("itemflag", "hide_enchants"),
            java.util.Map.entry("moonphase", "full moon"),
            java.util.Map.entry("potionaction", "added"),
            java.util.Map.entry("potioncause", "command"),
            java.util.Map.entry("potioneffecttype", "speed"),
            java.util.Map.entry("potioneffecttypecategory", "beneficial"),
            java.util.Map.entry("quitreason", "disconnected"),
            java.util.Map.entry("resourcepackstate", "successfully_loaded"),
            java.util.Map.entry("respawnreason", "death"),
            java.util.Map.entry("textalignment", "left"),
            java.util.Map.entry("transformreason", "infection"),
            java.util.Map.entry("unleashreason", "distance"),
            java.util.Map.entry("wolfvariant", "minecraft:pale")
        );
        StringBuilder failures = new StringBuilder();
        samples.forEach((code, sample) -> {
            Type<?> t = types.byCodeName(code);
            if (t == null || t.parse(sample).isEmpty()) {
                failures.append(code).append("('").append(sample).append("') ");
            }
        });
        assertTrue(failures.isEmpty(), "types failing to parse their sample: " + failures);
    }
}
