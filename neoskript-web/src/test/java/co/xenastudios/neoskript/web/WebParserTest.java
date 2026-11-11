/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The browser parser, with the committed syntax export (website/src/data/syntax-export.json). */
class WebParserTest {

    private static String data;
    private static SyntaxRegistry registry;

    @BeforeAll
    static void load() throws IOException {
        data = Files.readString(Path.of(System.getProperty("neoskript.syntaxExport")));
        registry = RegistrySource.CURRENT.load(data);
    }

    @Test
    void saysWhatItParsesAgainst() {
        assertTrue(RegistrySource.CURRENT.describe(data).matches("NeoSkript \\S+ on Paper \\S+"),
                RegistrySource.CURRENT.describe(data));
    }

    @Test
    void reportsProblemsAndAnOutline() {
        WebParser parser = new WebParser(registry);
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
    void acceptsScriptsNeoSkriptLoads() throws IOException {
        // The real-world corpus loads without errors on a NeoSkript server (the harness's
        // neoskript-real-world-corpus scenario), and so do the page's examples but the one with mistakes.
        List<SourceFile> corpus = new ArrayList<>(read(Path.of("../test-harness/corpus/real-world")));
        corpus.add(new SourceFile("corpus-assert.sk", Files.readString(Path.of("../test-harness/scripts/corpus-assert.sk"))));
        // Each example is pasted alone; some define the same functions as the corpus.
        List<List<SourceFile>> loads = new ArrayList<>(List.of(corpus));
        read(Path.of("../website/src/parser/examples")).stream()
                .filter(f -> !f.name().equals("mistakes.sk"))
                .forEach(f -> loads.add(List.of(f)));
        List<String> errors = new ArrayList<>();
        for (List<SourceFile> files : loads) {
            for (ParsedScript script : new ScriptLoader(registry).load(files)) {
                script.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR).map(Diagnostic::toString)
                        .forEach(errors::add);
            }
        }
        assertEquals(List.of(), errors);
    }

    @Test
    void quotesStrings() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", Json.quote("a\"b\\c\nd\u0001"));
        assertEquals("a\"b\\c\nd\u0001", Json.parse(Json.quote("a\"b\\c\nd\u0001")));
    }

    private static List<SourceFile> read(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            List<SourceFile> files = new ArrayList<>();
            for (Path path : paths.filter(p -> p.toString().endsWith(".sk")).sorted().toList()) {
                files.add(new SourceFile(path.getFileName().toString(), Files.readString(path)));
            }
            return files;
        }
    }
}
