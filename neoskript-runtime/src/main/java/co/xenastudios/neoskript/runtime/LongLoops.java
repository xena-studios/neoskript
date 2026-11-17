/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Says where a loop is that has run for seconds without waiting. Such a loop holds up its thread (the server's main
 * thread on Paper, a region's on Folia) as it does in Skript, and it keeps running, as in Skript. But the console is
 * told the script and line before the server's watchdog steps in, whose thread dump can't show them: compiled scripts
 * are hidden classes, which stack traces leave out.
 *
 * <p>Compiled loops call {@link #check} every {@link #EVERY} times round, so a loop pays one test of its counter per
 * time round.
 */
public final class LongLoops {

    /** How often, in times round a loop, compiled code calls {@link #check}: a power of two, tested with a mask. */
    public static final int EVERY = 1 << 16;

    /** How long a loop runs without waiting before the console hears of it, in milliseconds: 5 seconds by default. */
    public static final String WARN_AFTER_PROPERTY = "neoskript.longLoopWarningMillis";

    /**
     * Each loop running on this thread, by its script and line: when its run was first checked, how far round it was,
     * and whether it was reported. Only the JDK's types are kept in the thread, so it can't hold on to the plugin.
     */
    private static final ThreadLocal<Map<String, long[]>> RUNS = new ThreadLocal<>();

    private static final int SINCE = 0;
    private static final int TIMES = 1;
    private static final int REPORTED = 2;

    private LongLoops() {}

    /**
     * Called when a trigger on this thread waits, and when one carries on after a wait: the thread wasn't held up
     * meanwhile, so every loop's run on it starts again. A loop that waits each time round is never reported.
     */
    public static void waited() {
        Map<String, long[]> runs = RUNS.get();
        if (runs != null && !runs.isEmpty()) {
            runs.clear();
        }
    }

    /**
     * Called by compiled code when a loop at {@code line} of {@code script} has gone round {@code times} times, a
     * multiple of {@link #EVERY}. Reports it once if this run of it has taken longer than five seconds so far.
     */
    public static void check(String script, int line, int times) {
        Map<String, long[]> runs = RUNS.get();
        if (runs == null) {
            runs = new HashMap<>();
            RUNS.set(runs);
        }
        String key = script + ':' + line;
        long[] run = runs.get(key);
        long now = System.nanoTime();
        long warnAfter = Long.getLong(WARN_AFTER_PROPERTY, 5_000L) * 1_000_000L;
        if (run == null) {
            if (runs.size() > 64) {
                // Loops that ended long ago.
                runs.values().removeIf(old -> now - old[SINCE] > 4 * warnAfter);
            }
            run = new long[] {now, Long.MAX_VALUE, 0};
            runs.put(key, run);
        }
        if (times <= run[TIMES]) {
            // A new run of the loop.
            run[SINCE] = now;
            run[REPORTED] = 0;
        } else if (run[REPORTED] == 0 && now - run[SINCE] >= warnAfter) {
            run[REPORTED] = 1;
            RuntimeErrors.logError("A loop in " + script + ", line " + line + ", has been running for "
                    + (now - run[SINCE]) / 1_000_000_000L + " seconds without waiting (" + times
                    + " times round). Nothing else runs on its thread until it ends.");
        }
        run[TIMES] = times;
    }
}
