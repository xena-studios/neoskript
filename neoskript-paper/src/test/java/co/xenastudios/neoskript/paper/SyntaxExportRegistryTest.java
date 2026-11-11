/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.registry.ExportedRegistry;
import co.xenastudios.neoskript.frontend.registry.SyntaxExport;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The standard library's syntax export, read back into a registry, parses scripts as the library's own registry
 * does, but for what needs a literal parser: the in-browser parser's registry is built this way.
 */
class SyntaxExportRegistryTest {

    @Test
    void parsesAsTheLibrary() throws IOException {
        Library library = new Library();
        StandardLibrary.register(library);
        String json = library.export("test", "none").toJson();
        SyntaxRegistry exported = ExportedRegistry.build(SyntaxExport.fromJson(json));

        List<SourceFile> files = new ArrayList<>();
        for (String dir : List.of("../test-harness/corpus/real-world", "../test-harness/corpus/differential",
                "../website/src/parser/examples")) {
            try (Stream<Path> paths = Files.walk(Path.of(dir))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".sk")).sorted().toList()) {
                    files.add(new SourceFile(path.toString(), Files.readString(path)));
                }
            }
        }
        // The real-world corpus calls the harness's check functions.
        files.add(new SourceFile("corpus-assert.sk", Files.readString(Path.of("../test-harness/scripts/corpus-assert.sk"))));
        List<String> expected = diagnostics(new ScriptLoader(library.registry()).load(files));
        List<String> actual = diagnostics(new ScriptLoader(exported).load(files));
        // Without the literal parsers, words aren't known to be items, so a list of them can split otherwise and get
        // Skript's warning that it's missing 'and' where the library's doesn't, or not where it does
        // ('stone sword, stone pickaxe, 16 bread and leather chestplate'). Everything else is the same.
        assertEquals(withoutListWarnings(expected), withoutListWarnings(actual));
    }

    private static List<String> withoutListWarnings(List<String> diagnostics) {
        return diagnostics.stream().filter(d -> !(d.startsWith("warning ") && d.contains("List is missing 'and'")))
                .toList();
    }

    private static List<String> diagnostics(List<ParsedScript> scripts) {
        return scripts.stream().flatMap(s -> s.diagnostics().stream()).map(Object::toString).toList();
    }
}
