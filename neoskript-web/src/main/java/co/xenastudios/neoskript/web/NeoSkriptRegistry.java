/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.web;

import co.xenastudios.neoskript.frontend.registry.ExportedRegistry;
import co.xenastudios.neoskript.frontend.registry.SyntaxExport;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import java.util.Map;

/**
 * NeoSkript's own registry, from the syntax export a real server wrote ({@code website/src/data/syntax-export.json}),
 * built by {@link ExportedRegistry}.
 */
final class NeoSkriptRegistry implements RegistrySource {

    @Override
    @SuppressWarnings("unchecked")
    public String describe(String data) {
        // Only the header, without reading the whole export again.
        Map<String, Object> root = (Map<String, Object>) Json.parse(data);
        return "NeoSkript " + root.get("neoskript") + " on " + root.get("server");
    }

    @Override
    public SyntaxRegistry load(String data) {
        return ExportedRegistry.build(SyntaxExport.fromJson(data));
    }
}
