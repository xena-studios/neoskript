/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The syntax export reads back what it writes. */
class SyntaxExportTest {

    /** A small export with something in every field. */
    static SyntaxExport sample() {
        SyntaxExport.ElementParser event = new SyntaxExport.ElementParser(List.of(), false, List.of(), false, false,
                false, null, false, null, false, false, false, 0);
        SyntaxExport.ElementParser name = new SyntaxExport.ElementParser(List.of(), false, List.of(), false, false,
                false, "reflection", true, new SyntaxExport.Property(0, true, Map.of("display", "display name"), "name"),
                false, false, false, -1);
        SyntaxExport.ElementParser stop = new SyntaxExport.ElementParser(List.of("CONDITION", "TYPE"), true,
                List.of(0, 2), true, true, true, null, false, null, true, true, true, -1);
        return new SyntaxExport(SyntaxExport.FORMAT, "0.1.0", "Paper 26.3", List.of(
                new SyntaxExport.Element("event", "break", List.of("[block] (break[ing]|min(e|ing)) [[of] %itemtypes%]"),
                        null, "Break / Mine", List.of(new SyntaxExport.EventValue("player", 0),
                                new SyntaxExport.EventValue("block", -1)), event),
                new SyntaxExport.Element("expression", "name", List.of("[the] (name|display:display name) of %objects%",
                        "%objects%'[s] name"), "string", null, List.of(), name),
                new SyntaxExport.Element("effect", "send", List.of("send \"quote\\\" and \\\\ back\tslash\" to %players%"),
                        null, null, List.of(), SyntaxExport.ElementParser.NONE),
                new SyntaxExport.Element("effect", "stop", List.of("stop <.+> %*type%", "halt", "stop all"),
                        null, null, List.of(), stop)),
                List.of(new SyntaxExport.Type("gamemode", "game mode", "game modes", List.of("survival", "creative"),
                                new SyntaxExport.TypeParser(List.of("game mode", "gamemode"),
                                        List.of("game modes", "gamemodes"), List.of(), true, true, false, false, false,
                                        List.of("adventure mode"), false, List.of())),
                        new SyntaxExport.Type("player", "player", "players", List.of(),
                                new SyntaxExport.TypeParser(List.of("player"), List.of("players"), List.of("entity"),
                                        false, false, true, true, true, List.of(), true, List.of("notch")))),
                List.of(new SyntaxExport.Function("floor", List.of(new FunctionInfo.Parameter("n", "number", false,
                        false)), "long", false),
                        new SyntaxExport.Function("broadcastAll", List.of(new FunctionInfo.Parameter("texts", "string",
                                true, true)), null, false)),
                new SyntaxExport.Parser(List.of(new SyntaxExport.Conversion("player", "location", false),
                        new SyntaxExport.Conversion("location", "block", true)),
                        Map.of("name", List.of("player", "itemtype")), Map.of("reflection", "(script )?reflection"),
                        List.of(new SyntaxExport.Scope(List.of("player", "block"), List.of("block"), List.of("player")),
                                new SyntaxExport.Scope(List.of(), List.of(), List.of())), 0, 1));
    }

    @Test
    void roundTrips() {
        SyntaxExport export = sample();
        String json = export.toJson();
        assertEquals(export, SyntaxExport.fromJson(json));
        assertEquals(json, SyntaxExport.fromJson(json).toJson());
    }

    @Test
    void refusesAnotherFormat() {
        assertThrows(IllegalArgumentException.class, () -> SyntaxExport.fromJson(
                "{\"format\": 1, \"elements\": [], \"types\": [], \"functions\": []}"));
    }

    @Test
    void readsUnicodeEscapes() {
        assertEquals("aéb", Json.read("\"a\\u00e9b\""));
        assertEquals(List.of(1L, 2.5, true, false), Json.read(" [1, 2.5, true , false] "));
    }
}
