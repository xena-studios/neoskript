/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.errors;

import co.xenastudios.neoskript.runtime.errors.ErrorSource.Location;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Skript's {@code Frame}: counts the runtime errors (or warnings) of a span of time, deciding which are
 * printed, which are skipped and which lines are put in timeout. Errors come from any thread (Folia's
 * regions), so it's synchronized; it's only touched when something goes wrong.
 */
public final class ErrorFrame {

    /**
     * The limits, as Skript's config's {@code runtime errors} section.
     *
     * @param totalLimit errors printed per frame
     * @param lineLimit errors printed from one line per frame
     * @param lineTimeoutLimit errors from one line in a frame that put it in timeout
     * @param timeoutDuration how many frames a line stays in timeout
     */
    public record Limits(int totalLimit, int lineLimit, int lineTimeoutLimit, int timeoutDuration) {
        /** Skript's defaults: 8 per frame, 2 from a line, timeout at 4, for 10 frames. */
        public static final Limits DEFAULT = new Limits(8, 2, 4, 10);
    }

    /**
     * What a frame saw, for printing at its end.
     *
     * @param totalErrors errors per line, printed or not
     * @param skippedErrors errors per line that weren't printed
     * @param newTimeouts lines put in timeout in this frame
     */
    public record Output(Map<Location, Integer> totalErrors, Map<Location, Integer> skippedErrors,
            Set<Location> newTimeouts, Limits limits) {}

    private final Limits limits;
    private int printed;
    private final Map<Location, Integer> lineTotals = new LinkedHashMap<>();
    private final Map<Location, Integer> lineSkipped = new LinkedHashMap<>();
    private final Map<Location, Integer> timeouts = new LinkedHashMap<>();

    public ErrorFrame(Limits limits) {
        this.limits = limits;
    }

    public Limits limits() {
        return limits;
    }

    /** Counts an error; whether it should be printed now. */
    public synchronized boolean add(RuntimeError error) {
        Location location = error.source().location();
        int lineTotal = lineTotals.merge(location, 1, Integer::sum);
        if (timeouts.containsKey(location)) {
            lineSkipped.merge(location, 1, Integer::sum);
            return false;
        }
        if (printed < limits.totalLimit() && lineTotal <= limits.lineLimit()) {
            printed++;
            return true;
        }
        lineSkipped.merge(location, 1, Integer::sum);
        if (lineTotal == limits.lineTimeoutLimit()) {
            timeouts.put(location, limits.timeoutDuration());
        }
        return false;
    }

    /** Starts the next frame: clears the counts and counts the timeouts down. */
    public synchronized void nextFrame() {
        printed = 0;
        lineTotals.clear();
        lineSkipped.clear();
        for (Iterator<Map.Entry<Location, Integer>> it = timeouts.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Location, Integer> entry = it.next();
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
            } else {
                it.remove();
            }
        }
    }

    /** A copy of what this frame saw so far. */
    public synchronized Output output() {
        Set<Location> newTimeouts = new LinkedHashSet<>();
        timeouts.forEach((location, left) -> {
            if (left == limits.timeoutDuration()) {
                newTimeouts.add(location);
            }
        });
        return new Output(new LinkedHashMap<>(lineTotals), new LinkedHashMap<>(lineSkipped), newTimeouts, limits);
    }

    /** Ends this frame: what it saw, then the next frame starts. */
    public synchronized Output advance() {
        Output output = output();
        nextFrame();
        return output;
    }
}
