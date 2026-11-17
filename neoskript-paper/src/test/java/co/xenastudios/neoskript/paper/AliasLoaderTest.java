/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * {@link AliasLoader} against a made-up server. What's expected comes from Skript 2.17.0, run with the same alias
 * files: which names each line stands for and in what order, how a name defined again adds to the alias, which name
 * an item is written with, and the problems with their messages.
 */
class AliasLoaderTest {

    /** A few materials, with the block states the anvil has. */
    private static final class FakeServer implements AliasLoader.Server {

        private static final Map<String, Map<String, String>> BLOCKS = new LinkedHashMap<>();
        private static final List<String> ITEMS = List.of("minecraft:stone", "minecraft:diamond", "minecraft:red_dye",
                "minecraft:redstone", "minecraft:lapis_lazuli", "minecraft:anvil", "minecraft:oak_planks",
                "minecraft:birch_planks", "minecraft:spruce_planks", "minecraft:carrot", "minecraft:cobblestone",
                "minecraft:emerald", "minecraft:apple", "minecraft:potion", "minecraft:glass_bottle",
                "minecraft:flint", "minecraft:clay_ball", "minecraft:amethyst_shard");

        static {
            BLOCKS.put("minecraft:stone", Map.of());
            BLOCKS.put("minecraft:anvil", Map.of("facing", "north"));
            BLOCKS.put("minecraft:oak_planks", Map.of());
            BLOCKS.put("minecraft:birch_planks", Map.of());
            BLOCKS.put("minecraft:spruce_planks", Map.of());
            BLOCKS.put("minecraft:carrots", Map.of());
            BLOCKS.put("minecraft:cobblestone", Map.of());
            BLOCKS.put("minecraft:water", Map.of());
        }

        int[] version = {26, 3, 0};

        @Override
        public String material(String id) {
            String key = id.startsWith("minecraft:") ? id : "minecraft:" + id.replace(':', '_');
            return ITEMS.contains(key) || BLOCKS.containsKey(key) ? key : null;
        }

        @Override
        public boolean isItem(String material) {
            return ITEMS.contains(material);
        }

        @Override
        public boolean isBlock(String material) {
            return BLOCKS.containsKey(material);
        }

        @Override
        public AliasLoader.Made item(String material, String components) {
            if (components != null && components.contains("nonsense")) {
                throw new IllegalArgumentException("Unknown component 'nonsense'");
            }
            return new AliasLoader.Made(material + (components == null ? "" : components), components == null ? "" : components);
        }

        @Override
        public AliasLoader.Made blockData(String material, String states) {
            Map<String, String> defaults = BLOCKS.get(material);
            if (defaults == null) {
                throw new IllegalArgumentException("not a block");
            }
            Map<String, String> all = new TreeMap<>(defaults);
            String inside = states.substring(1, states.length() - 1);
            if (!inside.isEmpty()) {
                for (String pair : inside.split(",")) {
                    String[] parts = pair.split("=");
                    if (!defaults.containsKey(parts[0]) || parts[1].equals("up")) {
                        throw new IllegalArgumentException("bad state");
                    }
                    all.put(parts[0], parts[1]);
                }
            }
            return new AliasLoader.Made(material + all, material + all);
        }

        @Override
        public int[] version() {
            return version;
        }

        @Override
        public List<String> materials() {
            List<String> all = new ArrayList<>(ITEMS);
            for (String block : BLOCKS.keySet()) {
                if (!all.contains(block)) {
                    all.add(block);
                }
            }
            return all;
        }
    }

    private final FakeServer server = new FakeServer();
    private final List<AliasLoader.Problem> problems = new ArrayList<>();

    private AliasTable load(String... files) {
        List<AliasLoader.Input> inputs = new ArrayList<>();
        for (int i = 0; i < files.length; i += 2) {
            inputs.add(new AliasLoader.Input(files[i], files[i + 1]));
        }
        return AliasLoader.load(server, inputs, problems);
    }

    private static List<String> materials(AliasTable table, String name) {
        AliasTable.Type type = table.get(name);
        return type == null ? null : type.items.stream().map(AliasTable.Item::material).toList();
    }

    /** What Skript writes an item of {@code material} with nothing applied as. */
    private static String plainName(AliasTable table, String material) {
        AliasTable.Names names = table.names(material);
        if (names == null) {
            return null;
        }
        for (AliasTable.Named named : names.items) {
            if (named.item().components() == null && named.item().states().isEmpty()) {
                return named.singular();
            }
        }
        return names.defaultItem != null ? names.defaultItem.singular() : names.items.getFirst().singular();
    }

    private List<String> messages() {
        return problems.stream().map(p -> p.file().name() + ":" + p.line() + ": " + p.message()).toList();
    }

    @Test
    void namesAndPlurals() {
        AliasTable table = load("custom.sk", """
                gems:
                	ruby¦s = minecraft:red_dye
                	pluralmid¦s thing = minecraft:clay_ball
                	spaced  out   name = minecraft:flint
                """);
        assertEquals(List.of("minecraft:red_dye"), materials(table, "ruby"));
        assertEquals(List.of("minecraft:red_dye"), materials(table, "rubys"));
        assertNull(table.get("rubies"));
        assertEquals("pluralmids thing", table.names("minecraft:clay_ball").defaultItem.plural());
        assertNotNull(table.get("pluralmids thing"));
        assertNotNull(table.get("spaced out name"));
        assertEquals("ruby", plainName(table, "minecraft:red_dye"));
        assertTrue(table.namedByFiles("minecraft:red_dye"), "red dye loses its own name, as in Skript");
        assertFalse(table.namedByFiles("minecraft:apple"));
        assertEquals(List.of(), messages());
    }

    @Test
    void aNameDefinedAgainAddsToTheAlias() {
        // Skript: 'ruby' is red dye or redstone, written 'ruby or ruby'; 'rubys' is the same alias.
        AliasTable table = load("custom.sk", "a:\n\truby¦s = minecraft:red_dye\n", "more.sk", "b:\n\truby = minecraft:redstone\n");
        assertEquals(List.of("minecraft:red_dye", "minecraft:redstone"), materials(table, "ruby"));
        assertSame(table.get("ruby"), table.get("rubys"));
        assertEquals("ruby", table.names("minecraft:redstone").defaultItem.plural(), "no ¦, so no plural");
    }

    @Test
    void anotherAliasIsCopiedAsItIsThen() {
        // Skript: 'gem' is ruby or sapphire, without the redstone added to ruby later. Components after an alias's
        // name are ignored.
        AliasTable table = load("a.sk", """
                gems:
                	ruby¦s = minecraft:red_dye
                	sapphire¦s = minecraft:lapis_lazuli
                	gem¦s = ruby, sapphire
                	gem two = ruby {minecraft:custom_model_data={floats:[3f]}}
                	ruby = minecraft:redstone
                """);
        assertEquals(List.of("minecraft:red_dye", "minecraft:lapis_lazuli"), materials(table, "gem"));
        assertNull(table.get("gem two").items.getFirst().components());
    }

    @Test
    void variationsFillInIdsAndComponents() {
        AliasTable table = load("woody.sk", """
                woody:
                	{woods}:
                		oak = minecraft:oak_-
                		birch = minecraft:birch_-
                		{default} = minecraft:spruce_-
                	{woods} plank thing¦s = -planks
                colours:
                	{tint}:
                		red = - {minecraft:custom_model_data={floats:[1f]}}
                		blue = - {minecraft:custom_model_data={floats:[2f]}}
                	{tint} gem¦s = minecraft:emerald
                """);
        assertEquals(List.of("minecraft:oak_planks"), materials(table, "oak plank thing"));
        assertEquals(List.of("minecraft:birch_planks"), materials(table, "birch plank things"));
        assertEquals(List.of("minecraft:spruce_planks"), materials(table, "plank thing"));
        assertEquals("[minecraft:custom_model_data={floats:[1f]}]", table.get("red gem").items.getFirst().components());
        assertEquals("[minecraft:custom_model_data={floats:[2f]}]", table.get("blue gems").items.getFirst().components());
        assertEquals(List.of(), messages());
    }

    @Test
    void namesComeInSkriptsOrder() {
        AliasLoader loader = new AliasLoader(server);
        assertEquals(List.of("big rock¦s", "big boulder¦s", " rock¦s", " boulder¦s"),
                loader.keyPattern("[big] (rock|boulder)¦s"));
        assertEquals(List.of("bottles o' enchanting", "xp bottle¦s", "experience bottle¦s", "bottle o' enchanting"),
                loader.keyPattern("(bottle[s] o' enchanting|xp bottle¦s|experience bottle¦s)"));
    }

    @Test
    void anItemIsWrittenWithItsLastNameAndABlockWithItsFirst() {
        // Skript 2.17.0: 'alpha thing' is written 'gamma thing'; an experience bottle 'bottle o' enchanting'; an
        // anvil, plain or facing north (its default), 'north anvil'; 'big rock' for cobblestone.
        AliasTable table = load("z.sk", """
                names:
                	(alpha|beta) thing¦s = minecraft:apple
                	gamma thing = minecraft:apple
                	(bottle[s] o' enchanting|xp bottle¦s|experience bottle¦s) = minecraft:emerald[relatedEntity=xp bottle]
                	north anvil = minecraft:anvil[facing=north]
                	south anvil = minecraft:anvil[facing=south]
                	plain anvil = minecraft:anvil
                	[big] (rock|boulder)¦s = minecraft:cobblestone
                """);
        assertEquals("gamma thing", plainName(table, "minecraft:apple"));
        assertEquals("bottle o' enchanting", plainName(table, "minecraft:emerald"));
        assertEquals("xp bottle", table.names("minecraft:emerald").defaultItem.item().relatedEntity());
        assertEquals("north anvil", table.names("minecraft:anvil").items.getFirst().singular());
        assertSame(table.get("north anvil").items.getFirst(), table.get("plain anvil").items.getFirst(),
                "a plain anvil faces north, so it's the north anvil's item");
        assertEquals(Map.of("facing", "south"), table.get("south anvil").items.getFirst().states());
        assertEquals("big rock", plainName(table, "minecraft:cobblestone"));
    }

    @Test
    void anItemsOwnNameJoinsAnAliasOfThatName() {
        // Skript: with 'stone = minecraft:diamond', 'stone' is a diamond or stone, and 'stones' isn't a name.
        AliasTable table = load("a.sk", "a:\n\tstone = minecraft:diamond\n");
        assertEquals(List.of("minecraft:diamond", "minecraft:stone"), materials(table, "stone"));
        assertNull(table.get("stones"));
        assertTrue(table.namedByFiles("minecraft:stone"));
        assertEquals("stone", plainName(table, "minecraft:stone"));
    }

    @Test
    void namesIgnoreCase() {
        // A fix: Skript keeps 'Ruby Two' as written and looks names up in lower case, so it never finds it.
        AliasTable table = load("a.sk", "a:\n\tRuby Two = minecraft:emerald\n\tcased = MINECRAFT:Clay_Ball\n");
        assertNotNull(table.get("ruby two"));
        assertEquals("Ruby Two", plainName(table, "minecraft:emerald"));
        assertEquals(List.of("minecraft:clay_ball"), materials(table, "cased"));
    }

    @Test
    void problemsAsSkriptGivesThem() {
        AliasTable table = load("bad.sk", """
                outside = minecraft:stone
                broken:
                	bad id = minecraft:not_a_real_item
                	{nope} thing = minecraft:stone
                	open [bracket = minecraft:stone
                	close] bracket = minecraft:stone
                	open (paren = minecraft:stone
                	loopy = loopy2
                	loopy2 = loopy
                	empty one =
                	just text line
                	ünïcødé gëm¦s = minecraft:amethyst_shard
                	ok after bad = minecraft:flint
                	{notvars}x:
                		a = minecraft:stone
                	{usel}:
                		a = -
                	nested:
                		x = minecraft:stone
                	[big] (pebble|grit) dup = minecraft:nope
                	bad state = minecraft:anvil[facing=up]
                	apple state = minecraft:apple[facing=north]
                	trailing = minecraft:flint,
                	shiny@an item thing = minecraft:emerald
                """, "empty.sk", "");
        List<String> expected = new ArrayList<>(List.of(
                "bad.sk:11: 'just text line' is not an entry (like 'name = value')",
                "bad.sk:1: Aliases must be put in sections",
                "bad.sk:3: Minecraft id minecraft:not_a_real_item is not valid",
                "bad.sk:4: The variation {nope} has not been defined before",
                "bad.sk:5: Section starting at character 5 ('[') must be closed",
                "bad.sk:6: Character 5 (']') closes a nonexistent section",
                "bad.sk:7: Section starting at character 5 ('(') must be closed",
                "bad.sk:8: Minecraft id loopy2 is not valid",
                "bad.sk:9: Minecraft id loopy is not valid",
                "bad.sk:10: Minecraft id  is not valid",
                "bad.sk:14: A section should be variation section, but {notvars}x is not valid variation name",
                "bad.sk:17: Variation has no Minecraft id or tags specified, so it is useless",
                "bad.sk:18: A section should be variation section, but nested is not valid variation name"));
        // Once for each name the line stands for, as in Skript.
        for (int i = 0; i < 4; i++) {
            expected.add("bad.sk:20: Minecraft id minecraft:nope is not valid");
        }
        expected.addAll(List.of(
                "bad.sk:21: Parsing block state [facing=up] failed!",
                "bad.sk:22: Parsing block state [facing=north] failed!",
                "bad.sk:23: Minecraft id  is not valid",
                "bad.sk:24: Undefined gender 'an item thing' at shiny@an item thing",
                "empty.sk:0: 'empty.sk' is empty"));
        assertEquals(expected, messages());
        // The rest still load, as in Skript: the unknown variation leaves 'thing'.
        assertEquals(List.of("minecraft:stone"), materials(table, "thing"));
        assertEquals(List.of("minecraft:amethyst_shard"), materials(table, "ünïcødé gëms"));
        assertEquals(List.of("minecraft:flint"), materials(table, "ok after bad"));
        assertEquals(List.of("minecraft:anvil"), materials(table, "bad state"));
        assertEquals(List.of("minecraft:emerald"), materials(table, "shiny"));
        assertNull(table.get("loopy"));
        assertNull(table.get("outside"));
    }

    @Test
    void whatStopsSkriptIsALineWithAnError() {
        // Each of these stops Skript 2.17.0 loading any aliases (an AssertionError, an exception or a class cast).
        AliasTable table = load("crash.sk", """
                crashes:
                	unknown var = {nope} minecraft:stone
                	states only = [facing=north]
                	no value = minecraft:anvil[facing]
                	lone} brace = minecraft:stone
                	bad components = minecraft:diamond {minecraft:nonsense=1}
                	{vars}:
                		inner:
                			a = minecraft:stone
                		b = minecraft:diamond
                	fine = minecraft:flint
                old:
                	minecraft version = banana
                	skipped = minecraft:flint
                """);
        assertEquals(List.of(
                "crash.sk:2: Missing space between the id and the components in {nope} minecraft:stone",
                "crash.sk:3: Missing id or - in [facing=north]",
                "crash.sk:4: Parsing block state [facing] failed!",
                "crash.sk:5: Character 4 ('}') closes a nonexistent section",
                "crash.sk:6: Failed to apply component data to ItemStack: Unknown component 'nonsense'",
                "crash.sk:8: Sections are not allowed here",
                "crash.sk:13: 'banana' is not a valid version string"), messages());
        assertEquals(List.of("minecraft:flint"), materials(table, "fine"));
        assertEquals(List.of("minecraft:stone"), materials(table, "lone} brace"));
        assertNull(table.get("skipped"));
    }

    @Test
    void minecraftVersionConditions() {
        AliasTable table = load("v.sk", """
                old:
                	minecraft version = 1.12 or older
                	ancient = minecraft:stone
                new:
                	minecraft version = 1.20.5 or newer
                	modern = minecraft:stone
                range:
                	minecraft version = 26.0 to 26.3
                	ranged = minecraft:stone
                exact:
                	minecraft version = 26.3
                	exact = minecraft:stone
                """);
        assertNull(table.get("ancient"));
        assertNotNull(table.get("modern"));
        assertNotNull(table.get("ranged"));
        assertNotNull(table.get("exact"));
    }

    @Test
    void hugeAliasesAreLeftOutQuickly() {
        String groups = "[a] ".repeat(30);
        StringBuilder many = new StringBuilder("many:\n");
        for (int i = 0; i < 50_000; i++) {
            many.append("\talias ").append(i).append(" = minecraft:stone\n");
        }
        AliasTable table = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> load("huge.sk", "huge:\n\t" + groups + "x = minecraft:stone\n\tafter = minecraft:flint\n",
                        "many.sk", many.toString()));
        assertEquals(List.of("huge.sk:2: This alias stands for more than " + AliasLoader.MAX_NAMES_PER_LINE
                + " names, so it isn't loaded"), messages());
        assertNotNull(table.get("after"));
        assertNotNull(table.get("alias 49999"));
    }

    @Test
    void aHugeVariationIsLeftOutAlone() {
        AliasTable table = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> load("v.sk",
                "v:\n\t{big}:\n\t\t" + "[a] ".repeat(30) + "x = minecraft:stone\n\t\tsmall = minecraft:fl-\n"
                        + "\t{big} thing = -int\n"));
        assertEquals(List.of("v.sk:3: This variation stands for more than " + AliasLoader.MAX_NAMES_PER_LINE
                + " names, so it isn't loaded"), messages());
        assertEquals(List.of("minecraft:flint"), materials(table, "small thing"));
    }

    @Test
    void repeatedProblemsStopRepeating() {
        String names = "(" + String.join("|", java.util.stream.IntStream.range(0, 500).mapToObj(i -> "n" + i).toList()) + ")";
        load("a.sk", "a:\n\t" + names + " = minecraft:nope\n");
        assertEquals(AliasLoader.MAX_REPEATS, problems.size());
    }

    @Test
    void garbageNeverThrows() {
        java.util.Random random = new java.util.Random(11);
        String alphabet = "ab =:{}[]()|¦-,#\t\n@'\"%\\é";
        for (int run = 0; run < 2000; run++) {
            StringBuilder text = new StringBuilder();
            int length = random.nextInt(200);
            for (int i = 0; i < length; i++) {
                text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            problems.clear();
            assertNotNull(load("fuzz.sk", text.toString()));
        }
    }
}
