package co.xenastudios.neoskript.core.parser.pattern;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxPatternTest {

    @Test
    void capturesArgumentsAndOptionalGroups() {
        SyntaxPattern pattern = PatternCompiler.compile("send %string% [to %player%]");
        assertEquals(2, pattern.argCount());

        Optional<List<String>> withoutTarget = pattern.match("send \"hi\"");
        assertTrue(withoutTarget.isPresent());
        assertEquals("\"hi\"", withoutTarget.get().get(0));
        assertNull(withoutTarget.get().get(1), "optional target should be null when absent");

        Optional<List<String>> withTarget = pattern.match("send \"hi\" to player");
        assertTrue(withTarget.isPresent());
        assertEquals("\"hi\"", withTarget.get().get(0));
        assertEquals("player", withTarget.get().get(1));
    }

    @Test
    void doesNotSplitOnKeywordsInsideQuotedStrings() {
        SyntaxPattern pattern = PatternCompiler.compile("send %string% [to %player%]");

        Optional<List<String>> noTarget = pattern.match("send \"Welcome to the server!\"");
        assertTrue(noTarget.isPresent());
        assertEquals("\"Welcome to the server!\"", noTarget.get().get(0));
        assertNull(noTarget.get().get(1), "the 'to' inside the string must not be treated as the separator");

        Optional<List<String>> withTarget = pattern.match("send \"Welcome to the server!\" to player");
        assertTrue(withTarget.isPresent());
        assertEquals("\"Welcome to the server!\"", withTarget.get().get(0));
        assertEquals("player", withTarget.get().get(1));
    }

    @Test
    void matchesAreCaseInsensitiveAndWhitespaceFlexible() {
        SyntaxPattern pattern = PatternCompiler.compile("broadcast %string%");
        assertTrue(pattern.match("BROADCAST \"hello\"").isPresent());
        assertTrue(pattern.match("broadcast    \"hello\"").isPresent());
    }

    @Test
    void rejectsNonMatchingInput() {
        SyntaxPattern pattern = PatternCompiler.compile("broadcast %string%");
        assertTrue(pattern.match("shout \"hello\"").isEmpty());
    }

    @Test
    void supportsAlternatives() {
        SyntaxPattern pattern = PatternCompiler.compile("(ban|kick) %player%");
        assertTrue(pattern.match("ban Notch").isPresent());
        assertTrue(pattern.match("kick Notch").isPresent());
        assertTrue(pattern.match("mute Notch").isEmpty());
    }
}
