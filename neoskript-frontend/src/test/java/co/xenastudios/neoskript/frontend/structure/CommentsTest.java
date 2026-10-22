/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The cases come from Skript's own tests in {@code misc/comments.sk}. */
class CommentsTest {

    private static void assertStripped(String expected, String line) {
        assertEquals(expected, Comments.strip(line).stripTrailing());
    }

    @Test
    void removesTrailingAndWholeLineComments() {
        assertStripped("set {_a} to {_b}", "set {_a} to {_b} # test");
        assertStripped("", "# whole line");
    }

    @Test
    void keepsHashesInStrings() {
        assertStripped("send \"#ff0000 red\" to player", "send \"#ff0000 red\" to player");
        assertStripped("assert \"###a\" is x", "assert \"###a\" is x");
        assertStripped("set {_b} to \"<##abcdef>test\"", "set {_b} to \"<##abcdef>test\"");
        assertStripped("send \"100%% #1\"", "send \"100%% #1\"");
        assertStripped("broadcast \"\"\"#quoted\"\"\"", "broadcast \"\"\"#quoted\"\"\" # c");
    }

    @Test
    void keepsHashesInStringsInsideExpressions() {
        assertStripped("assert \"a%\"#\"%\" is x", "assert \"a%\"#\"%\" is x");
        assertStripped("assert \"#a%\"#}\"%\" is x", "assert \"#a%\"#}\"%\" is x # c");
        assertStripped("send \"%{_x}% #1\"", "send \"%{_x}% #1\" # trailing");
    }

    @Test
    void keepsHashesInVariableNames() {
        assertStripped("set {_a##} to \"test\"", "set {_a##} to \"test\"");
        assertStripped("set {##_b::%\"{}#\"%} to \"#test\"", "set {##_b::%\"{}#\"%} to \"#test\"");
    }

    @Test
    void hashInAnExpressionInAVariableNameStartsAComment() {
        assertStripped("set {##_b::%\"{}\"", "set {##_b::%\"{}\"#%} to \"#test\"");
    }

    @Test
    void doubleHashOutsideStringsIsOneHash() {
        assertStripped("set {_x} to 5 # not a comment", "set {_x} to 5 ## not a comment");
    }
}
