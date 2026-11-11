/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The syntax export reads back what it writes. */
class SyntaxExportTest {

    @Test
    void roundTrips() {
        SyntaxExport export = new SyntaxExport(SyntaxExport.FORMAT, "0.1.0", "Paper 26.3", List.of(
                new SyntaxExport.Element("event", "break", List.of("[block] (break[ing]|min(e|ing)) [[of] %itemtypes%]"),
                        null, "Break / Mine", List.of(new SyntaxExport.EventValue("player", 0),
                                new SyntaxExport.EventValue("block", -1))),
                new SyntaxExport.Element("expression", "name", List.of("[the] name of %objects%",
                        "%objects%'[s] name"), "string", null, List.of()),
                new SyntaxExport.Element("effect", "send", List.of("send \"quote\\\" and \\\\ back\tslash\" to %players%"),
                        null, null, List.of())),
                List.of(new SyntaxExport.Type("gamemode", "game mode", "game modes", List.of("survival", "creative")),
                        new SyntaxExport.Type("player", "player", "players", List.of())),
                List.of(new SyntaxExport.Function("floor", List.of(new FunctionInfo.Parameter("n", "number", false,
                        false)), "long", false),
                        new SyntaxExport.Function("broadcastAll", List.of(new FunctionInfo.Parameter("texts", "string",
                                true, true)), null, false)));
        String json = export.toJson();
        assertEquals(export, SyntaxExport.fromJson(json));
        assertEquals(json, SyntaxExport.fromJson(json).toJson());
    }

    @Test
    void refusesAnotherFormat() {
        assertThrows(IllegalArgumentException.class, () -> SyntaxExport.fromJson(
                "{\"format\": 2, \"elements\": [], \"types\": [], \"functions\": []}"));
    }

    @Test
    void readsUnicodeEscapes() {
        assertEquals("aéb", Json.read("\"a\\u00e9b\""));
        assertEquals(List.of(1L, 2.5, true, false), Json.read(" [1, 2.5, true , false] "));
    }
}
