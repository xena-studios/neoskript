/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.PausingSection;
import co.xenastudios.neoskript.api.element.TriggerContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The pausing sections each running trigger is inside ({@code transaction on {db}:}), so a section the trigger
 * leaves without reaching its end (a {@code stop}, a {@code return}, a jump past it, an error, being dropped)
 * hears of it at once through {@link PausingSection#abandoned}. Called by the generated code.
 */
public final class PausingSections {

    private static final Map<TriggerContext, Deque<PausingSection>> OPEN = new IdentityHashMap<>();
    /** Where an addon's hook that throws is reported. */
    private static volatile Consumer<String> problems = message -> {};

    private PausingSections() {}

    /**
     * Where problems go: a hook that threw.
     *
     * @param sink the console, or {@code null} to drop them
     */
    public static void install(Consumer<String> sink) {
        problems = sink == null ? message -> {} : sink;
    }

    /** The trigger's code entered section {@code section}. */
    public static void enter(PausingSection section, TriggerContext context) {
        synchronized (OPEN) {
            OPEN.computeIfAbsent(context, ignored -> new ArrayDeque<>()).push(section);
        }
    }

    /** The trigger reached the end of section {@code section}: it isn't abandoned. */
    public static void leave(PausingSection section, TriggerContext context) {
        remove(section, context);
    }

    /** The trigger left section {@code section} another way: it's told. */
    public static void abandon(PausingSection section, TriggerContext context) {
        if (remove(section, context)) {
            tell(section, context);
        }
    }

    /** The trigger stopped with an error: every section it was inside is told, innermost first. */
    public static void abandonAll(TriggerContext context) {
        Deque<PausingSection> open;
        synchronized (OPEN) {
            open = OPEN.remove(context);
        }
        if (open != null) {
            open.forEach(section -> tell(section, context));
        }
    }

    /** Every trigger is dropped, as the server stops: every section still open is told. */
    public static void abandonEverything() {
        List<Map.Entry<TriggerContext, Deque<PausingSection>>> open;
        synchronized (OPEN) {
            open = new ArrayList<>(OPEN.entrySet());
            OPEN.clear();
        }
        for (Map.Entry<TriggerContext, Deque<PausingSection>> entry : open) {
            entry.getValue().forEach(section -> tell(section, entry.getKey()));
        }
    }

    /** How many triggers are inside a pausing section now. */
    public static int open() {
        synchronized (OPEN) {
            return OPEN.size();
        }
    }

    private static boolean remove(PausingSection section, TriggerContext context) {
        synchronized (OPEN) {
            Deque<PausingSection> open = OPEN.get(context);
            if (open == null) {
                return false;
            }
            boolean removed = open.removeFirstOccurrence(section);
            if (open.isEmpty()) {
                OPEN.remove(context);
            }
            return removed;
        }
    }

    private static void tell(PausingSection section, TriggerContext context) {
        try {
            section.abandoned(context);
        } catch (RuntimeException | LinkageError e) {
            problems.accept("A pausing section's abandoned hook failed: " + e);
        }
    }
}
