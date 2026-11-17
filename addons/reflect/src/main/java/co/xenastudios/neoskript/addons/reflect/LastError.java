/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

/**
 * skript-reflect's {@code [the] [last] [java] (throwable|exception|error)}: what the last Java call on this thread
 * threw, or {@code null} once one succeeds. skript-reflect keeps one for the whole server; on Folia, where regions
 * run at once, each thread has its own, so one region's call can't hide another's error.
 */
final class LastError {

    private static final ThreadLocal<Throwable> LAST = new ThreadLocal<>();

    private LastError() {}

    static Throwable get() {
        return LAST.get();
    }

    static void set(Throwable error) {
        LAST.set(error);
    }

    static void clear() {
        LAST.remove();
    }
}
