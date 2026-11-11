/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.frontend.stub.SkriptSyntax;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebParserTest {

    private static String syntaxData() throws IOException {
        try (InputStream in = WebParserTest.class.getResourceAsStream("/skript-syntax/" + SkriptSyntax.VERSION + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void readsTheDocsExportAsGsonDoes() throws IOException {
        // The browser build can't use Gson, so its reader has to give the corpus tests' syntax exactly.
        assertEquals(SkriptSyntax.load(), SkriptDocsRegistry.read(syntaxData()));
        assertEquals("Skript 2.17.0 syntax", RegistrySource.CURRENT.describe(syntaxData()));
    }

    @Test
    void reportsProblemsAndAnOutline() throws IOException {
        WebParser parser = new WebParser(RegistrySource.CURRENT.load(syntaxData()));
        String result = parser.parse("""
                on join:
                \tsend "hi" to player
                \tfrobnicate the player

                command /hello:
                \ttrigger:
                \t\tsend "hello"
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) Json.parse(result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> diagnostics = (List<Map<String, Object>>) json.get("diagnostics");
        assertEquals(1, diagnostics.size(), result);
        assertEquals("error", diagnostics.getFirst().get("severity"));
        assertEquals(3.0, diagnostics.getFirst().get("line"));
        assertTrue(diagnostics.getFirst().get("message").toString().contains("frobnicate"), result);
        assertEquals(List.of(Map.of("kind", "event", "line", 1.0, "text", "on join:"),
                Map.of("kind", "command", "line", 5.0, "text", "command /hello:")), json.get("outline"));
    }

    @Test
    void quotesStrings() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", Json.quote("a\"b\\c\nd\u0001"));
        assertEquals("a\"b\\c\nd\u0001", Json.parse(Json.quote("a\"b\\c\nd\u0001")));
    }
}
