/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.njol.skript.SkriptAPIException;
import ch.njol.skript.SkriptConfig;
import ch.njol.skript.conditions.base.PropertyCondition;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Version;
import ch.njol.util.Kleenean;
import ch.njol.util.StringUtils;
import java.util.List;
import org.bukkit.event.Event;
import org.junit.jupiter.api.Test;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.util.Priority;

/** Skript's API as NeoSkriptCompat writes it: the parts that work without a server behave as Skript's. */
class SkriptApiTest {

    @Test
    void propertyPatternsAreSkripts() {
        assertArrayEquals(new String[] {"[the] (min|:max) height of %worlds%", "%worlds%'[s] (min|:max) height"},
                PropertyExpression.getPatterns("(min|:max) height", "worlds"));
        assertArrayEquals(new String[] {"[the] name [of %players%]", "%players%'[s] name"},
                PropertyExpression.getDefaultPatterns("name", "players"));
        assertArrayEquals(new String[] {"%blocks% (is|are) locked", "%blocks% (isn't|is not|aren't|are not) locked"},
                PropertyCondition.getPatterns(PropertyCondition.PropertyType.BE, "locked", "blocks"));
        assertArrayEquals(new String[] {"%players% (has|have) x", "%players% (doesn't|does not|do not|don't) have x"},
                PropertyCondition.getPatterns(PropertyCondition.PropertyType.HAVE, "x", "players"));
    }

    @Test
    void checkIsSkripts() {
        Integer[] values = {1, 2, 3};
        // And: all of them; or: any of them; negated flips it; nothing to check is false, or true negated.
        assertTrue(SimpleExpression.check(values, v -> v > 0, false, true));
        assertFalse(SimpleExpression.check(values, v -> v > 1, false, true));
        assertTrue(SimpleExpression.check(values, v -> v > 2, false, false));
        assertTrue(SimpleExpression.check(values, v -> v > 1, true, true));
        assertFalse(SimpleExpression.check(new Integer[0], v -> true, false, true));
        assertTrue(SimpleExpression.check(new Integer[0], v -> true, true, true));
        assertTrue(SimpleExpression.check(new Integer[] {null, 1}, v -> v == 1, false, true));
    }

    @Test
    void getSingleOfSeveralThrows() {
        SimpleExpression<String> two = new SimpleExpression<>() {
            @Override
            protected String[] get(Event event) {
                return new String[] {"a", null, "b"};
            }

            @Override
            public boolean isSingle() {
                return false;
            }

            @Override
            public Class<? extends String> getReturnType() {
                return String.class;
            }

            @Override
            public boolean init(ch.njol.skript.lang.Expression<?>[] e, int p, Kleenean d,
                    ch.njol.skript.lang.SkriptParser.ParseResult r) {
                return true;
            }

            @Override
            public String toString(Event event, boolean debug) {
                return "two";
            }
        };
        assertArrayEquals(new String[] {"a", "b"}, two.getArray(null));
        assertArrayEquals(new String[] {"a", "b"}, two.getAll(null));
        assertThrows(SkriptAPIException.class, () -> two.getSingle(null));
    }

    @Test
    void versionsCompareAsSkripts() {
        Version version = new Version(2, 17, 0);
        assertTrue(new Version(2, 14, 999).isSmallerThan(version));
        assertFalse(version.isSmallerThan(new Version("2.15")));
        assertEquals("2.17", version.toString());
        assertEquals(0, new Version("2.17").compareTo(version));
    }

    @Test
    void prioritiesOrderSyntax() {
        List<Priority> sorted = new java.util.ArrayList<>(List.of(SyntaxInfo.PATTERN_MATCHES_EVERYTHING,
                PropertyExpression.DEFAULT_PRIORITY, SyntaxInfo.SIMPLE));
        sorted.sort(null);
        assertEquals(List.of(SyntaxInfo.SIMPLE, PropertyExpression.DEFAULT_PRIORITY,
                SyntaxInfo.PATTERN_MATCHES_EVERYTHING), sorted);
        assertTrue(SyntaxInfo.SIMPLE.compareTo(SyntaxInfo.COMBINED) < 0);
    }

    @Test
    void prioritiesAreSkriptsLevels() {
        assertEquals(co.xenastudios.neoskript.api.registration.SyntaxPriority.SIMPLE,
                CompatPriority.level(SyntaxInfo.SIMPLE));
        assertEquals(co.xenastudios.neoskript.api.registration.SyntaxPriority.EVENT_VALUE,
                CompatPriority.level(ch.njol.skript.expressions.base.EventValueExpression.DEFAULT_PRIORITY));
        assertEquals(co.xenastudios.neoskript.api.registration.SyntaxPriority.COMBINED,
                CompatPriority.level(SyntaxInfo.COMBINED));
        assertEquals(co.xenastudios.neoskript.api.registration.SyntaxPriority.PROPERTY,
                CompatPriority.level(PropertyExpression.DEFAULT_PRIORITY));
        assertEquals(co.xenastudios.neoskript.api.registration.SyntaxPriority.PROPERTY,
                CompatPriority.level(PropertyCondition.DEFAULT_PRIORITY));
        assertEquals(co.xenastudios.neoskript.api.registration.SyntaxPriority.PATTERN_MATCHES_EVERYTHING,
                CompatPriority.level(SyntaxInfo.PATTERN_MATCHES_EVERYTHING));
    }

    @Test
    void priorityFromPatternsAsSkripts() {
        assertEquals(SyntaxInfo.SIMPLE, CompatSyntaxInfo.estimate(List.of("server is paused")));
        assertEquals(SyntaxInfo.COMBINED, CompatSyntaxInfo.estimate(List.of("chunk (:x|z) of %chunk%")));
        assertEquals(SyntaxInfo.PATTERN_MATCHES_EVERYTHING,
                CompatSyntaxInfo.estimate(List.of("make %entity% damage %entity%", "%number% %number%")));
        assertEquals(SyntaxInfo.PATTERN_MATCHES_EVERYTHING, CompatSyntaxInfo.estimate(List.of("%a% [%b%]")));
        assertEquals(SyntaxInfo.PATTERN_MATCHES_EVERYTHING, CompatSyntaxInfo.estimate(List.of("<.+> x")));
        assertEquals(SyntaxInfo.SIMPLE, CompatSyntaxInfo.estimate(List.of("100\\% sure")));
        assertEquals(SyntaxInfo.COMBINED, SyntaxInfo.builder(ch.njol.skript.lang.Effect.class)
                .addPattern("kill %entities%").build().priority());
        assertEquals(SyntaxInfo.SIMPLE, SyntaxInfo.builder(ch.njol.skript.lang.Effect.class)
                .addPattern("kill %entities%").priority(SyntaxInfo.SIMPLE).build().priority());
    }

    @Test
    void configHasSkriptsDefaults() {
        assertEquals(100, SkriptConfig.maxTargetBlockDistance.value());
        assertEquals(false, SkriptConfig.caseSensitive.value());
    }

    @Test
    void textHelpers() {
        assertEquals("a, b", StringUtils.join(List.of("a", "b"), ", "));
        assertEquals("1st 2nd 3rd 4th 11th 12th 13th 21st", String.join(" ", StringUtils.fancyOrderNumber(1),
                StringUtils.fancyOrderNumber(2), StringUtils.fancyOrderNumber(3), StringUtils.fancyOrderNumber(4),
                StringUtils.fancyOrderNumber(11), StringUtils.fancyOrderNumber(12), StringUtils.fancyOrderNumber(13),
                StringUtils.fancyOrderNumber(21)));
        assertEquals("1.5", StringUtils.toString(1.5, 2));
        assertEquals("2", StringUtils.toString(2.0, 2));
    }

    @Test
    void pluralsOfTypeNames() {
        assertEquals(List.of("worlds", "world"), CompatTypes.singulars("worlds"));
        assertTrue(CompatTypes.singulars("entities").contains("entity"));
        assertTrue(CompatTypes.singulars("boxes").contains("box"));
        assertEquals(List.of("chunk"), CompatTypes.singulars("chunk"));
    }
}
