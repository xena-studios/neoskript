/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import co.xenastudios.neoskript.frontend.pattern.PatternCompiler;
import co.xenastudios.neoskript.frontend.pattern.PatternIndex;
import co.xenastudios.neoskript.frontend.stub.SkriptSyntax;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every pattern the reference Skript registers compiles, and the index narrows lines down. */
class SkriptPatternsTest {

    private static final SkriptSyntax SYNTAX = SkriptSyntax.load();

    @Test
    void everyPatternCompiles() {
        List<String> failures = new ArrayList<>();
        int count = 0;
        for (SkriptSyntax.Element element : SYNTAX.allElements()) {
            for (String pattern : element.patterns()) {
                count++;
                try {
                    PatternCompiler.compile(pattern);
                } catch (IllegalArgumentException e) {
                    failures.add(element.id() + ": " + e.getMessage());
                }
            }
        }
        assertEquals(List.of(), failures);
        assertTrue(count > 2000, "only " + count + " patterns");
    }

    @Test
    void indexNarrowsEffectsDownToAFewCandidates() {
        PatternIndex<String> index = new PatternIndex<>();
        for (SkriptSyntax.Element effect : SYNTAX.effects()) {
            for (String pattern : effect.patterns()) {
                index.add(PatternCompiler.compile(pattern), effect.id());
            }
        }
        List<String> candidates = index.candidates("teleport player to {home::%uuid of player%}").stream()
                .map(PatternIndex.Entry::owner)
                .distinct()
                .toList();
        assertTrue(candidates.contains("EffTeleport"), candidates::toString);
        assertTrue(candidates.size() < index.size() / 10,
                candidates.size() + " candidates out of " + index.size() + " patterns");
    }

    @Test
    void sendMatchesWithAnyPlaceholderText() {
        CompiledPattern send = SYNTAX.effects().stream()
                .filter(e -> e.id().equals("EffMessage"))
                .flatMap(e -> e.patterns().stream())
                .map(PatternCompiler::compile)
                .findFirst()
                .orElseThrow();
        assertNotNull(send.match("send \"hi %player%\" to player", (input, start, end, spec) -> "x"));
    }
}
