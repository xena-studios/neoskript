/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.stub;

import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.parse.Statement;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compares parse results with what the reference Skript does with the same scripts.
 *
 * <p>The reference Skript loads every corpus script without errors (the harness checks that), so any
 * error outside a {@code parse:} block is a false error. Skript's tests put code that should or shouldn't
 * parse in {@code parse:} blocks, then assert on {@code last parse logs}: that assertion says whether
 * Skript reported a problem for the block.
 */
public final class CorpusCheck {

    private static final Pattern EXPECT_CLEAN =
            Pattern.compile("assert (the )?last parse logs? (is|are) not set.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPECT_ERROR = Pattern.compile(
            "assert (the )?(first element of )?(the )?last parse logs? (is set|are set|is \"|are \"|is \\{|contains?|matches)"
                    + ".*",
            Pattern.CASE_INSENSITIVE);

    /**
     * @param falseErrors errors in code Skript accepts
     * @param expectedErrors {@code parse:} blocks where Skript reports a problem
     * @param caughtErrors of those, the blocks where we report one too
     * @param expectedClean {@code parse:} blocks Skript accepts
     * @param falselyRejected of those, the blocks where we report a problem (also false errors)
     * @param missed descriptions of the blocks where Skript reports a problem and we don't
     */
    public record Result(int files, long lines, List<Diagnostic> falseErrors, int expectedErrors, int caughtErrors,
            int expectedClean, List<String> falselyRejected, List<String> missed) {

        public int totalFalseErrors() {
            return falseErrors.size() + falselyRejected.size();
        }
    }

    private CorpusCheck() {}

    public static Result check(List<ParsedScript> scripts) {
        List<Diagnostic> falseErrors = new ArrayList<>();
        Counter counter = new Counter();
        long lines = 0;
        for (ParsedScript script : scripts) {
            lines += script.file().lines().size();
            // Warnings don't stop code loading; Skript gives some for its own tests too (empty sections).
            script.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR).forEach(falseErrors::add);
            structures(script, script.structures(), counter);
        }
        return new Result(scripts.size(), lines, falseErrors, counter.expectedErrors, counter.caughtErrors,
                counter.expectedClean, counter.falselyRejected, counter.missed);
    }

    private static final class Counter {
        int expectedErrors;
        int caughtErrors;
        int expectedClean;
        final List<String> falselyRejected = new ArrayList<>();
        final List<String> missed = new ArrayList<>();
    }

    private static void structures(ParsedScript script, List<ParsedStructure> structures, Counter counter) {
        for (ParsedStructure structure : structures) {
            switch (structure) {
                case ParsedStructure.Event event -> body(script, event.body(), counter);
                case ParsedStructure.Command command -> body(script, command.statements(), counter);
                case ParsedStructure.Function function -> body(script, function.body(), counter);
                case ParsedStructure.Custom custom -> body(script, custom.body(), counter);
                // Top-level parse blocks store their results in a variable; nothing says what's expected.
                case ParsedStructure.Parse parse -> structures(script, parse.code(), counter);
                case ParsedStructure.Data data -> {}
                case ParsedStructure.AutoReload autoReload -> {}
            }
        }
    }

    private static void body(ParsedScript script, List<Statement> body, Counter counter) {
        for (int i = 0; i < body.size(); i++) {
            Statement statement = body.get(i);
            if (statement instanceof Statement.Parse parse) {
                Boolean expectError = expectation(body, i + 1);
                String where = script.file().name() + ":" + parse.line().number();
                if (Boolean.TRUE.equals(expectError)) {
                    counter.expectedErrors++;
                    if (!parse.captured().isEmpty()) {
                        counter.caughtErrors++;
                    } else {
                        counter.missed.add(where);
                    }
                } else if (Boolean.FALSE.equals(expectError)) {
                    counter.expectedClean++;
                    if (!parse.captured().isEmpty()) {
                        counter.falselyRejected.add(where + ": " + parse.captured());
                    }
                }
                body(script, parse.body(), counter);
            } else if (statement instanceof Statement.Section section) {
                body(script, section.body(), counter);
            }
        }
    }

    /** What the assertion after a {@code parse:} block expects: an error, no error, or unknown (null). */
    private static Boolean expectation(List<Statement> body, int from) {
        for (int i = from; i < body.size(); i++) {
            Statement statement = body.get(i);
            if (statement instanceof Statement.Parse) {
                return null;
            }
            String text = statement.line().text();
            if (EXPECT_CLEAN.matcher(text).matches()) {
                return false;
            }
            if (EXPECT_ERROR.matcher(text).matches()) {
                return true;
            }
        }
        return null;
    }
}
