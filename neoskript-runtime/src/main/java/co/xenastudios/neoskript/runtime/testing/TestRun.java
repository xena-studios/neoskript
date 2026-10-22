/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.testing;

import java.util.ArrayList;
import java.util.List;

/**
 * Results of the test that's running, for Skript's test syntax ({@code test "name":}, {@code assert}).
 * Tests run one at a time, so this is a single, static record.
 */
public final class TestRun {

    private static final List<String> FAILURES = new ArrayList<>();
    private static boolean skipped;

    private TestRun() {}

    /** Starts recording a test. */
    public static synchronized void begin() {
        FAILURES.clear();
        skipped = false;
    }

    /** Called by a failing {@code assert}. */
    public static synchronized void fail(String script, int line, String message) {
        FAILURES.add(message + " (" + script + ", line " + line + ")");
    }

    /** Called when a test's {@code when} condition is false. */
    public static synchronized void skip() {
        skipped = true;
    }

    public static synchronized boolean skipped() {
        return skipped;
    }

    public static synchronized List<String> failures() {
        return List.copyOf(FAILURES);
    }

    /** Records an error thrown by the test, which fails it. */
    public static synchronized void error(Throwable error) {
        FAILURES.add("Error: " + error.getMessage());
    }
}
