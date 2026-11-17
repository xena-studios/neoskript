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

    /** Each loop running on each thread, by its script and line, while it runs. */
    private static final PerThread<Map<String, Run>> RUNS = new PerThread<>();

    /** One run of a loop: when it was first checked, how far round it was, and whether it was reported. */
    private static final class Run {
        long since;
        int times;
        boolean reported;
    }

    private LongLoops() {}

    /**
     * Called by compiled code when a loop at {@code line} of {@code script} has gone round {@code times} times, a
     * multiple of {@link #EVERY}. Reports it once if this run of it has taken longer than five seconds so far.
     */
    public static void check(String script, int line, int times) {
        Map<String, Run> runs = RUNS.get();
        if (runs == null) {
            runs = new HashMap<>();
            RUNS.set(runs);
        }
        String key = script + ':' + line;
        Run run = runs.get(key);
        long now = System.nanoTime();
        long warnAfter = Long.getLong(WARN_AFTER_PROPERTY, 5_000L) * 1_000_000L;
        if (run == null) {
            if (runs.size() > 64) {
                // Loops that ended long ago.
                runs.values().removeIf(old -> now - old.since > 4 * warnAfter);
            }
            run = new Run();
            runs.put(key, run);
            run.times = Integer.MAX_VALUE;
        }
        if (times <= run.times) {
            // A new run of the loop.
            run.since = now;
            run.reported = false;
        } else if (!run.reported && now - run.since >= warnAfter) {
            run.reported = true;
            RuntimeErrors.logError("A loop in " + script + ", line " + line + ", has been running for "
                    + (now - run.since) / 1_000_000_000L + " seconds without waiting (" + times
                    + " times round). Nothing else runs on its thread until it ends.");
        }
        run.times = times;
    }
}
