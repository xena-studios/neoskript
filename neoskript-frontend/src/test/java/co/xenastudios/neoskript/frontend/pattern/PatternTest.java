/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PatternTest {

    /**
     * A stand-in for the real expression parser: numbers, quoted strings, {variables}, and the words
     * "player" and "victim". Anything is accepted as "object(s)".
     */
    private static final PlaceholderResolver STUB = (input, start, end, spec) -> {
        String text = input.substring(start, end);
        for (String type : spec.types()) {
            boolean ok = switch (type.replaceAll("s$", "")) {
                case "number", "integer" -> text.matches("-?\\d+(\\.\\d+)?");
                case "string", "text" -> text.matches("\"([^\"]|\"\")*\"") || text.startsWith("{");
                case "player", "entitie", "entity", "commandsender", "command sender" ->
                        text.equals("player") || text.equals("victim") || text.startsWith("{");
                case "object" -> true;
                default -> false;
            };
            if (ok) {
                return text;
            }
        }
        return null;
    };

    private static MatchResult match(String pattern, String input) {
        return PatternCompiler.compile(pattern).match(input, STUB);
    }

    private static List<Object> values(String pattern, String input) {
        MatchResult result = match(pattern, input);
        assertNotNull(result, () -> "'" + pattern + "' should match '" + input + "'");
        return result.values();
    }

    @Test
    void literalsIgnoreCaseAndWhitespaceAmount() {
        assertNotNull(match("stop the server", "STOP   the Server"));
        assertNull(match("stop the server", "stop theserver"));
        assertNull(match("stop", "stopped"));
    }

    @Test
    void optionalPartsAndTheSpacesAroundThem() {
        String pattern = "[the] (health|hp) of %players%";
        assertEquals(List.of("player"), values(pattern, "health of player"));
        assertEquals(List.of("player"), values(pattern, "the hp of player"));
        assertNull(match(pattern, "thehealth of player"));
        assertNotNull(match("a [b] [c] d", "a d"));
        assertNotNull(match("a [b] [c] d", "a c d"));
        assertNull(match("a [b] c", "ac"));
    }

    @Test
    void optionalPlaceholdersAreNullWhenUnused() {
        String pattern = "send %strings% [to %commandsenders%]";
        assertEquals(Arrays.asList("\"hi\"", null), values(pattern, "send \"hi\""));
        assertEquals(List.of("\"hi to you\"", "player"), values(pattern, "send \"hi to you\" to player"));
        MatchResult result = match(pattern, "send \"hi\" to player");
        assertEquals(List.of(5, 9), Arrays.stream(result.spans().get(0)).boxed().toList());
        assertEquals(List.of(13, 19), Arrays.stream(result.spans().get(1)).boxed().toList());
        assertNull(match(pattern, "send \"hi\"").spans().get(1));
    }

    @Test
    void placeholdersDontSplitStringsVariablesOrBrackets() {
        assertEquals(List.of("{_x::%player%}", "\"a to b\""),
                values("set %objects% to %objects%", "set {_x::%player%} to \"a to b\""));
        assertEquals(List.of("(1 to 2)", "3"), values("set %objects% to %objects%", "set (1 to 2) to 3"));
        assertEquals(List.of("\"say \"\"to\"\" me\""), values("broadcast %strings%", "broadcast \"say \"\"to\"\" me\""));
    }

    @Test
    void placeholdersTryTheShortestTextFirst() {
        assertEquals(List.of("a", "b to c"), values("set %objects% to %objects%", "set a to b to c"));
    }

    @Test
    void choicesWithMarksCombineByXor() {
        String pattern = "(1¦add|2¦remove|3¦set) %number% [(4¦twice)]";
        assertEquals(1, match(pattern, "add 5").mark());
        assertEquals(3, match(pattern, "set 5").mark());
        assertEquals(1 ^ 4, match(pattern, "add 5 twice").mark());
    }

    @Test
    void parseTags() {
        String pattern = "[:all] (player|first:first player|:last player)";
        assertEquals(Set.of(), match(pattern, "player").tags());
        assertEquals(Set.of("all", "first"), match(pattern, "all first player").tags());
        assertEquals(Set.of("last player"), match(pattern, "LAST player").tags());
        assertEquals(Set.of("implicit"), match("implicit:<.+>", "anything").tags());
    }

    @Test
    void regexes() {
        MatchResult result = match("(exit|stop) <-?\\d+(_\\d+)*> (section|loop|conditional)s", "exit 2 loops");
        assertEquals("2", ((java.util.regex.MatchResult) result.regexValues().getFirst()).group());
        assertNull(match("(exit|stop) <-?\\d+(_\\d+)*> (section|loop|conditional)s", "exit x loops"));
        assertEquals(List.of("{_x}", "{_y}"), values("%objects% if <.+>[,] (otherwise|else) %objects%",
                "{_x} if 1 is 1, otherwise {_y}"));
    }

    @Test
    void lessThanIsTextWhenNoRegexFollows() {
        String pattern = "%objects% ((is|are) less than|<) %objects%";
        assertEquals(List.of("1", "2"), values(pattern, "1 < 2"));
        assertEquals(List.of("1", "2"), values(pattern, "1 is less than 2"));
    }

    @Test
    void escapes() {
        assertNotNull(match("chance of %number%[(\\%)]", "chance of 50%"));
        assertNotNull(match("%objects% where \\[<.+>\\]", "{_x::*} where [input > 1]"));
    }

    @Test
    void placeholderSpecs() {
        PatternElement element = PatternCompiler.compile("%-*~item type/entity types@-1%").root();
        PlaceholderSpec spec = ((PatternElement.Placeholder) element).spec();
        assertEquals(new PlaceholderSpec(List.of("item type", "entity types"), true, true, true, -1), spec);
        assertEquals(3, PatternCompiler.compile("%a% [%b%] (%c%|x)").placeholderCount());
    }

    @Test
    void reportsInvalidPatterns() {
        for (String pattern : List.of("(a|b", "[a", "a)", "%number", "%number@x%", "%/x%", "<[>", "a\\")) {
            assertThrows(InvalidPatternException.class, () -> PatternCompiler.compile(pattern), pattern);
        }
    }

    @Test
    void leadingLiteralsForTheIndex() {
        assertEquals(Set.of("health", "hp", "the"), PatternCompiler.compile("[the] (health|hp) of %players%")
                .leadingLiterals());
        assertNull(PatternCompiler.compile("%players%'s health").leadingLiterals());
        assertNull(PatternCompiler.compile("[the] %players%").leadingLiterals());
        assertNull(PatternCompiler.compile("[a]").leadingLiterals());
    }

    @Test
    void indexReturnsCandidatesInRegistrationOrder() {
        PatternIndex<String> index = new PatternIndex<>();
        index.add(PatternCompiler.compile("send %strings%"), "send");
        index.add(PatternCompiler.compile("%objects% is set"), "is set");
        index.add(PatternCompiler.compile("[the] health of %players%"), "health");
        index.add(PatternCompiler.compile("-%number%"), "negate");
        index.add(PatternCompiler.compile("set %objects% to %objects%"), "set");
        assertEquals(List.of("send", "is set"), owners(index.candidates("Send \"hi\"")));
        assertEquals(List.of("is set", "health"), owners(index.candidates("the health of player")));
        assertEquals(List.of("is set", "negate"), owners(index.candidates("-5")));
        assertEquals(List.of("is set"), owners(index.candidates("{_x} is set")));
    }

    private static List<String> owners(List<PatternIndex.Entry<String>> entries) {
        List<String> owners = new ArrayList<>();
        entries.forEach(e -> owners.add(e.owner()));
        return owners;
    }

    @Test
    void spanCandidates() {
        assertEquals(List.of(1, 3, 7), ends("a b {c}"));
        assertEquals(List.of(2, 6), ends("\"\" \"x\" }"), "stops at a closing brace it didn't open");
        assertEquals(List.of(1, 9, 11), ends("a (b c d) e"));
        assertTrue(ends(" a").isEmpty());
    }

    private static List<Integer> ends(String input) {
        return Arrays.stream(Spans.candidateEnds(input, 0)).boxed().toList();
    }
}
