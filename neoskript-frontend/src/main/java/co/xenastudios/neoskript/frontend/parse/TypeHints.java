/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Skript's local variable type hints, for scripts {@code using local variable type hints}: what types each
 * simple local variable may hold at each line, so a variable of the wrong type is an error while parsing
 * ({@code Expected variable '{_x}' to be a text, but it is an integer}).
 *
 * <p>Hints are kept in scopes, one per section. A section starts with its outer scope's hints; when it
 * ends, its hints join the outer ones, as it may or may not have run. A section that stops the trigger
 * passes nothing on; {@code exit 2 sections} passes its hints straight to where the trigger carries on.
 */
final class TypeHints {

    private static final class Scope {
        final Map<String, Set<String>> hints;
        /** Whether execution can't carry on from here: nothing more joins the outer scope. */
        boolean frozen;

        Scope(Map<String, Set<String>> hints) {
            this.hints = hints;
        }
    }

    private final Deque<Scope> scopes = new ArrayDeque<>();
    private boolean enabled;
    private boolean suppressed;

    TypeHints() {
        scopes.push(new Scope(new HashMap<>()));
    }

    void enable(boolean on) {
        enabled = on;
    }

    /** Whether hints are collected and checked: the script uses them, and they aren't suppressed. */
    boolean active() {
        return enabled && !suppressed;
    }

    boolean suppressed() {
        return suppressed;
    }

    void suppress(boolean off) {
        suppressed = off;
    }

    /** Starts a trigger or function, with no hints but {@code initial}. */
    void reset(Map<String, Set<String>> initial) {
        scopes.clear();
        Map<String, Set<String>> hints = new HashMap<>();
        initial.forEach((name, types) -> hints.put(name, new LinkedHashSet<>(types)));
        scopes.push(new Scope(hints));
    }

    /** The types {@code variable} (a simple local variable's name, like {@code _x} or {@code _x::*}) may be. */
    Set<String> get(String variable) {
        Set<String> types = scopes.peek().hints.get(key(variable));
        return types == null ? Set.of() : types;
    }

    void set(String variable, Set<String> types) {
        if (!active() || scopes.peek().frozen) {
            return;
        }
        if (types.isEmpty()) {
            delete(variable);
            return;
        }
        scopes.peek().hints.put(key(variable), new LinkedHashSet<>(types));
        listOf(variable, types);
    }

    void add(String variable, Set<String> types) {
        if (!active() || scopes.peek().frozen || types.isEmpty()) {
            return;
        }
        scopes.peek().hints.computeIfAbsent(key(variable), k -> new LinkedHashSet<>()).addAll(types);
        listOf(variable, types);
    }

    /** {@code set {_x::1} to 10}: the list {@code {_x::*}} may hold integers too. */
    private void listOf(String variable, Set<String> types) {
        int separator = variable.lastIndexOf("::");
        if (separator > 0 && !variable.endsWith("::*")) {
            scopes.peek().hints.computeIfAbsent(key(variable.substring(0, separator) + "::*"), k -> new LinkedHashSet<>())
                    .addAll(types);
        }
    }

    /** {@code delete {_x}}; deleting {@code {_x::*}} deletes what's under it too. */
    void delete(String variable) {
        if (!active() || scopes.peek().frozen) {
            return;
        }
        String key = key(variable);
        scopes.peek().hints.remove(key);
        if (key.endsWith("::*")) {
            String prefix = key.substring(0, key.length() - 1);
            scopes.peek().hints.keySet().removeIf(other -> other.startsWith(prefix));
        }
    }

    /** Enters a section: it starts with the hints so far. */
    void enter() {
        Map<String, Set<String>> copy = new HashMap<>();
        scopes.peek().hints.forEach((name, types) -> copy.put(name, new LinkedHashSet<>(types)));
        scopes.push(new Scope(copy));
    }

    /** Leaves a section: what it found joins the outer hints, unless execution couldn't get out of it. */
    void exit() {
        Scope inner = scopes.pop();
        if (!inner.frozen) {
            join(inner, scopes.peek());
        }
    }

    /**
     * Execution stops for {@code levels} sections here ({@code exit 2 sections}), or for the whole trigger if
     * {@code levels} is negative: the hints so far go to where it carries on. With {@code conditional}, as
     * in {@code exit 1 section if ...}, execution may also carry on here.
     */
    void stop(int levels, boolean conditional) {
        Scope current = scopes.peek();
        if (levels >= 0) {
            int i = 0;
            for (Scope scope : scopes) {
                if (i++ == levels) {
                    join(current, scope);
                    break;
                }
            }
        }
        if (!conditional) {
            current.frozen = true;
        }
    }

    private static void join(Scope from, Scope into) {
        from.hints.forEach((name, types) -> into.hints.computeIfAbsent(name, k -> new LinkedHashSet<>()).addAll(types));
    }

    private static String key(String variable) {
        return variable.toLowerCase(java.util.Locale.ROOT);
    }
}
