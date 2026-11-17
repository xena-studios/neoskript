/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import org.slf4j.Logger;

/** NeoSkript's start, which always ends with the API ready, so addons waiting for it never wait forever. */
final class Startup {

    private Startup() {}

    /**
     * Runs {@code work}, then {@code ready}, even if {@code work} fails: the failure is logged, and addons get on
     * with what did load.
     */
    static void run(Runnable work, Runnable ready, Logger logger) {
        try {
            work.run();
        } catch (RuntimeException | LinkageError e) {
            logger.error("NeoSkript couldn't finish starting; scripts or variables may be missing", e);
        } finally {
            ready.run();
        }
    }
}
