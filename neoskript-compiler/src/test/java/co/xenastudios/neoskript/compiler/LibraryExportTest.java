/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.frontend.registry.SyntaxExport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The syntax export says which addon registered what. */
class LibraryExportTest {

    @Test
    void namesTheAddonThatRegisteredEachElementTypeAndFunction() {
        Library library = new Library();
        library.type("string", "text", String.class);
        library.effect("Say", info -> (context, args) -> {}, "say %string%");
        library.registeringAddon("NeoSkriptDb");
        library.type("database", "database", Object.class);
        library.effect("NeoSkriptDbEffExecute", info -> (context, args) -> {}, "execute %string%");
        library.function("dbPing", List.of(Parameter.of("host", "string")), null, false, args -> null);
        // Refused or already NeoSkript's: not credited to the addon.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> library.effect("Say", info -> (context, args) -> {}, "say again %string%"));
        library.type("string", "text", String.class);
        library.registeringAddon(null);
        library.effect("Shout", info -> (context, args) -> {}, "shout %string%");

        SyntaxExport export = SyntaxExport.fromJson(library.export("0.1.0", "test").toJson());
        assertNull(element(export, "Say").addon());
        assertEquals("NeoSkriptDb", element(export, "NeoSkriptDbEffExecute").addon());
        assertNull(element(export, "Shout").addon());
        assertEquals("NeoSkriptDb", export.types().stream().filter(t -> t.id().equals("database")).findFirst()
                .orElseThrow().addon());
        assertNull(export.types().stream().filter(t -> t.id().equals("string")).findFirst().orElseThrow().addon());
        assertEquals("NeoSkriptDb", export.functions().stream().filter(f -> f.name().equals("dbPing")).findFirst()
                .orElseThrow().addon());
    }

    private static SyntaxExport.Element element(SyntaxExport export, String id) {
        return export.elements().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }
}
