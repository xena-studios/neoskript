/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.registry.DelayState;
import co.xenastudios.neoskript.frontend.structure.Line;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed script.
 *
 * @param diagnostics problems to report, not counting those captured by {@code parse:} sections
 * @param delays whether the trigger may have waited before each statement, by the statement's line
 *     (compared by identity)
 */
public record ParsedScript(SourceFile file, List<ParsedStructure> structures, List<Diagnostic> diagnostics,
        Map<Line, DelayState> delays) {

    public ParsedScript {
        structures = List.copyOf(structures);
        diagnostics = List.copyOf(diagnostics);
        delays = Collections.unmodifiableMap(new IdentityHashMap<>(delays));
    }

    /** Whether the trigger may have waited before the statement on {@code line}. */
    public DelayState delayBefore(Line line) {
        return delays.getOrDefault(line, DelayState.NOT_DELAYED);
    }
}
