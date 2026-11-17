/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
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
import java.util.Set;

/**
 * A parsed script.
 *
 * @param diagnostics problems to report, not counting those captured by {@code parse:} sections
 * @param delays whether the trigger may have waited before each statement, by the statement's line
 *     (compared by identity)
 * @param suppressions the tags of the warnings the script suppresses at each statement's or structure's line
 *     ({@code deprecated}), compared by identity
 */
public record ParsedScript(SourceFile file, List<ParsedStructure> structures, List<Diagnostic> diagnostics,
        Map<Line, DelayState> delays, Map<Line, Set<String>> suppressions, Map<Line, String> statementErrors,
        Map<Line, WaitingSection> waitingSections, Map<Line, Statement> sectionStatements) {

    /**
     * A section Skript loads as a trigger of its own ({@code spawn a pig:}) whose code may wait, so it fails. Skript
     * then keeps only the first error its code gave, which may be one only compiling finds.
     *
     * @param id the section's or its section expression's syntax id
     * @param name its name in the error ({@code spawn})
     * @param body its code
     * @param firstErrorLine the line of the first error parsing its code gave, or -1
     */
    public record WaitingSection(String id, String name, List<Statement> body, int firstErrorLine) {}

    public ParsedScript(SourceFile file, List<ParsedStructure> structures, List<Diagnostic> diagnostics,
            Map<Line, DelayState> delays, Map<Line, Set<String>> suppressions, Map<Line, String> statementErrors) {
        this(file, structures, diagnostics, delays, suppressions, statementErrors, Map.of(), Map.of());
    }

    public ParsedScript(SourceFile file, List<ParsedStructure> structures, List<Diagnostic> diagnostics,
            Map<Line, DelayState> delays, Map<Line, Set<String>> suppressions) {
        this(file, structures, diagnostics, delays, suppressions, Map.of());
    }

    /** The section on {@code line} that failed because its code may wait, or {@code null}. */
    public WaitingSection waitingSection(Line line) {
        return line == null ? null : waitingSections.get(line);
    }

    /**
     * The line of a section header that nothing would take the section of, read as a statement, for the compiler:
     * Skript keeps that statement's error if it has one. {@code null} if there's none.
     */
    public Statement sectionStatement(Line line) {
        return line == null ? null : sectionStatements.get(line);
    }

    /**
     * The error the section header on {@code line} gives read as a statement, which Skript reports instead when the
     * header fails, or {@code null}.
     */
    public String statementError(Line line) {
        return line == null ? null : statementErrors.get(line);
    }

    public ParsedScript {
        statementErrors = Collections.unmodifiableMap(new IdentityHashMap<>(statementErrors));
        waitingSections = Collections.unmodifiableMap(new IdentityHashMap<>(waitingSections));
        sectionStatements = Collections.unmodifiableMap(new IdentityHashMap<>(sectionStatements));
        structures = List.copyOf(structures);
        diagnostics = List.copyOf(diagnostics);
        delays = Collections.unmodifiableMap(new IdentityHashMap<>(delays));
        suppressions = Collections.unmodifiableMap(new IdentityHashMap<>(suppressions));
    }

    public ParsedScript(SourceFile file, List<ParsedStructure> structures, List<Diagnostic> diagnostics,
            Map<Line, DelayState> delays) {
        this(file, structures, diagnostics, delays, Map.of());
    }

    /** Whether the trigger may have waited before the statement on {@code line}. */
    public DelayState delayBefore(Line line) {
        return delays.getOrDefault(line, DelayState.NOT_DELAYED);
    }

    /** Whether the script suppresses the warnings tagged {@code tag} at {@code line}. */
    public boolean suppresses(Line line, String tag) {
        Set<String> tags = line == null ? null : suppressions.get(line);
        return tags != null && tags.contains(tag);
    }
}
