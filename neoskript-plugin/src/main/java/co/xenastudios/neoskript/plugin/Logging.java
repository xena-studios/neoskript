/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.plugin.NeoSkriptConfig.Verbosity;

/** config.yml's {@code verbosity}, as Skript's: how much NeoSkript logs. */
final class Logging {

    private static volatile Verbosity verbosity = Verbosity.NORMAL;
    /** Whether events are logged as they run, read for every event, so a plain field. */
    private static volatile boolean events;

    private Logging() {}

    static void verbosity(Verbosity level) {
        verbosity = level;
        events = level.logs(Verbosity.VERY_HIGH);
    }

    /** Whether NeoSkript logs what Skript logs at {@code level}. */
    static boolean logs(Verbosity level) {
        return verbosity.logs(level);
    }

    /** Whether each event is logged with the triggers it runs, at {@code very high} and {@code debug}. */
    static boolean events() {
        return events;
    }
}
