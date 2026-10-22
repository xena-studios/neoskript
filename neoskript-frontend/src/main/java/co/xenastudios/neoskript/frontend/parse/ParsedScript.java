/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import java.util.List;

/**
 * A parsed script.
 *
 * @param diagnostics problems to report, not counting those captured by {@code parse:} sections
 */
public record ParsedScript(SourceFile file, List<ParsedStructure> structures, List<Diagnostic> diagnostics) {

    public ParsedScript {
        structures = List.copyOf(structures);
        diagnostics = List.copyOf(diagnostics);
    }
}
