/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web;

import java.util.logging.Logger;

/**
 * The addon's warnings in the console, at most {@value #PER_WINDOW} every ten seconds, so a script that floods the
 * web with failing requests can't flood the console too. What's left out is counted, and said when the next window
 * opens.
 */
public final class Console {

    static final int PER_WINDOW = 10;
    private static final long WINDOW = 10_000_000_000L;

    private final Logger logger;
    private long windowStart = System.nanoTime();
    private int given;
    private int left;

    /**
     * Warnings to a logger.
     *
     * @param logger where they go
     */
    public Console(Logger logger) {
        this.logger = logger;
    }

    /**
     * A warning, unless too many have been given lately.
     *
     * @param message the warning
     */
    public void warn(String message) {
        String summary = null;
        boolean show;
        synchronized (this) {
            long now = System.nanoTime();
            if (now - windowStart >= WINDOW) {
                if (left > 0) {
                    summary = left + " more NeoSkriptWeb warnings were left out in the last ten seconds";
                }
                windowStart = now;
                given = 0;
                left = 0;
            }
            show = given < PER_WINDOW;
            if (show) {
                given++;
            } else {
                left++;
            }
        }
        if (summary != null) {
            logger.warning(summary);
        }
        if (show) {
            logger.warning(message);
        }
    }
}
