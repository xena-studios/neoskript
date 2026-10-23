/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Skript's runtime errors: problems syntax reports while a trigger runs, which don't stop it. Inside a
 * {@code catch runtime errors:} section they're collected for {@code last caught runtime errors};
 * elsewhere they're logged.
 */
public final class RuntimeErrors {

    private static final ThreadLocal<Deque<List<String>>> CATCHING = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<List<String>> LAST_CAUGHT = ThreadLocal.withInitial(List::of);
    private static volatile Consumer<String> uncaught = message -> {};

    private RuntimeErrors() {}

    /** Where errors outside {@code catch runtime errors:} go: the plugin logs them. */
    public static void install(Consumer<String> log) {
        uncaught = log;
    }

    /** Reports a runtime error. */
    public static void report(String message) {
        Deque<List<String>> catching = CATCHING.get();
        if (catching.isEmpty()) {
            uncaught.accept(message);
        } else {
            catching.peek().add(message);
        }
    }

    /** Starts a {@code catch runtime errors:} section. */
    public static void begin() {
        CATCHING.get().push(new ArrayList<>());
    }

    /** Ends the innermost {@code catch runtime errors:} section, keeping what it caught. */
    public static void end() {
        Deque<List<String>> catching = CATCHING.get();
        if (!catching.isEmpty()) {
            LAST_CAUGHT.set(List.copyOf(catching.pop()));
        }
    }

    /** What the last {@code catch runtime errors:} section caught. */
    public static Object[] lastCaught() {
        return LAST_CAUGHT.get().toArray();
    }
}
