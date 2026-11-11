/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A registry built from a syntax export has what the export says. */
class ExportedRegistryTest {

    private final SyntaxRegistry registry = ExportedRegistry.build(
            SyntaxExport.fromJson(SyntaxExportTest.sample().toJson()));

    private SyntaxInfo syntax(SyntaxKind kind, String id) {
        return registry.syntaxes(kind).stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void types() {
        assertEquals("gamemode", registry.resolveType("game modes").type().id());
        assertTrue(registry.resolveType("game modes").plural());
        assertEquals(Set.of("survival", "creative"), registry.type("gamemode").literals());
        assertTrue(registry.type("player").openLiterals());
        assertFalse(registry.type("gamemode").openLiterals());
        assertTrue(registry.isSubtype("player", "entity"));
        assertTrue(registry.isStrict("gamemode"));
        assertTrue(registry.isEnumerable("gamemode"));
        assertTrue(registry.isConvertible("player", "location"));
        assertTrue(registry.isPlaceholderConversion("location", "block"));
        assertFalse(registry.isPlaceholderConversion("player", "location"));
        assertTrue(registry.hasPropertyItself("player", "name"));
        assertEquals(0, registry.typeOrder("gamemode"));
    }

    @Test
    void literalWords() {
        TypeInfo gamemode = registry.type("gamemode");
        assertTrue(registry.parses(gamemode, "Adventure  Mode"));
        assertFalse(registry.parses(gamemode, "survival mode"));
        TypeInfo player = registry.type("player");
        // Listed: certainly one. Not listed: might be one, but not certainly.
        assertTrue(registry.isLiteral(player, "Notch"));
        assertFalse(registry.isLiteral(player, "jeb_"));
        assertTrue(registry.mightBeLiteral(player, "jeb_"));
        assertTrue(registry.parses(player, "jeb_"));
    }

    @Test
    void elements() {
        SyntaxInfo stop = syntax(SyntaxKind.EFFECT, "stop");
        assertEquals(RegexRole.CONDITION, stop.regexRole(0));
        assertEquals(RegexRole.TYPE, stop.regexRole(1));
        assertTrue(registry.delays(stop));
        assertTrue(registry.stops(stop, 0));
        assertFalse(registry.stops(stop, 1));
        assertTrue(registry.stops(stop, 2));
        assertTrue(registry.ends(stop));
        assertTrue(registry.suppressesWarnings(stop));
        assertTrue(registry.sectionOnly(stop));
        assertEquals("string", syntax(SyntaxKind.EXPRESSION, "name").returnType());
        assertNull(syntax(SyntaxKind.EFFECT, "send").returnType());

        SyntaxInfo name = syntax(SyntaxKind.EXPRESSION, "name");
        assertEquals(new SyntaxRegistry.Experimental("reflection", true), registry.experimental(name));
        SyntaxRegistry.PropertySyntax property = registry.propertySyntax(name);
        assertEquals("display name", property.property().apply(Set.of("display")));
        assertEquals("name", property.property().apply(Set.of()));
        assertTrue(property.counts());
        assertNull(registry.experimental(stop));

        assertEquals("reflection", registry.experimentName("Script  Reflection"));
        assertEquals("something else", registry.experimentName("something else"));
        assertEquals(List.of("long"), registry.functions("floor").stream().map(FunctionInfo::returnType).toList());
    }

    @Test
    void eventValues() {
        EventValues values = registry.eventValues();
        SyntaxInfo stop = syntax(SyntaxKind.EFFECT, "stop");
        SyntaxInfo send = syntax(SyntaxKind.EFFECT, "send");
        EventValues.Scope onBreak = values.event(syntax(SyntaxKind.EVENT, "break"));
        TypeRef player = registry.resolveType("player");
        TypeRef blocks = new TypeRef(new TypeInfo("block", "block", Set.of(), false), true);
        // 'stop' is marked as an event value expression in the sample.
        assertTrue(onBreak.allows(stop, player, "event-player"));
        assertTrue(onBreak.allows(stop, blocks, "event-blocks"));
        assertFalse(onBreak.allows(stop, registry.resolveType("game mode"), "event-gamemode"));
        assertTrue(onBreak.allows(send, registry.resolveType("game mode"), "event-gamemode"));
        assertFalse(values.function().allows(stop, player, "event-player"));
        assertTrue(values.keepsEventValues(stop));
        assertFalse(values.keepsEventValues(send));
        assertNull(values.structure(stop));

        PlaceholderSpec players = new PlaceholderSpec(List.of("player"), false, false, false, 0);
        // A library element's left-out player is the event's.
        assertTrue(onBreak.allowsLeftOut(send, players));
        // 'player' also has a default value, which is enough alone.
        assertTrue(values.function().allowsLeftOut(send, players));
        PlaceholderSpec playersOrModes = new PlaceholderSpec(List.of("player", "game mode"), false, false, false, 0);
        assertTrue(values.function().allowsLeftOut(send, playersOrModes));
    }
}
