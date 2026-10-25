/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.runtime.errors.ErrorFrame;
import co.xenastudios.neoskript.runtime.errors.ErrorSites;
import co.xenastudios.neoskript.runtime.errors.ErrorSource;
import co.xenastudios.neoskript.runtime.errors.RuntimeError;
import co.xenastudios.neoskript.runtime.errors.RuntimeError.Level;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Skript's runtime errors and warnings: problems syntax reports while a trigger runs, which don't stop it.
 * Inside a {@code catch runtime errors:} section they're collected for {@code last caught runtime errors};
 * elsewhere they go to the installed {@link Consumer}, limited per frame as Skript's
 * {@code RuntimeErrorFilter} does, with where they came from (the script, line and syntax element).
 */
public final class RuntimeErrors {

    /** Where errors go: Skript's {@code RuntimeErrorConsumer}. */
    public interface Consumer {
        /** Prints an error that passed the frame's limits. */
        void printError(RuntimeError error);

        /** Prints the end of a frame: what was skipped, lines put in timeout, and the notifications. */
        void printFrameOutput(ErrorFrame.Output output, Level level);
    }

    /** As Skript's catcher: at most this many errors are kept, against a runaway loop. */
    private static final int CATCH_LIMIT = 1000;

    private static final ThreadLocal<Deque<List<String>>> CATCHING = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<List<String>> LAST_CAUGHT = ThreadLocal.withInitial(List::of);
    private static volatile Consumer consumer;
    private static volatile ErrorFrame errorFrame = new ErrorFrame(ErrorFrame.Limits.DEFAULT);
    private static volatile ErrorFrame warningFrame = new ErrorFrame(ErrorFrame.Limits.DEFAULT);

    private RuntimeErrors() {}

    /** Where errors outside {@code catch runtime errors:} go, and the limits per frame for errors and warnings. */
    public static void install(Consumer to, ErrorFrame.Limits errors, ErrorFrame.Limits warnings) {
        errorFrame = new ErrorFrame(errors);
        warningFrame = new ErrorFrame(warnings);
        consumer = to;
    }

    /**
     * Ends a frame, as Skript's frame task every {@code frame duration}: prints what the error and the
     * warning frames saw and starts the next ones.
     */
    public static void nextFrame() {
        Consumer to = consumer;
        ErrorFrame.Output errors = errorFrame.advance();
        ErrorFrame.Output warnings = warningFrame.advance();
        if (to != null) {
            to.printFrameOutput(errors, Level.ERROR);
            to.printFrameOutput(warnings, Level.WARNING);
        }
    }

    /** Reports a runtime error. */
    public static void report(String message) {
        emit(Level.ERROR, message, null);
    }

    /** Reports a runtime error, highlighting the first match of the regular expression {@code highlight} in its line. */
    public static void report(String message, String highlight) {
        emit(Level.ERROR, message, highlight);
    }

    /** Reports a runtime warning. */
    public static void warning(String message) {
        emit(Level.WARNING, message, null);
    }

    /** Reports a runtime warning, highlighting the first match of {@code highlight} in its line. */
    public static void warning(String message, String highlight) {
        emit(Level.WARNING, message, highlight);
    }

    /**
     * Where an error reported now would come from, for code that reports one later, off the trigger's
     * thread; null outside compiled code.
     */
    public static ErrorSource source() {
        ErrorSites.Site site = ErrorSites.current();
        return site == null ? null : site.source();
    }

    /** Reports a runtime error from {@code source}, as found earlier with {@link #source()}. */
    public static void report(ErrorSource source, String message) {
        emit(Level.ERROR, message, null, source);
    }

    private static void emit(Level level, String message, String highlight) {
        Deque<List<String>> catching = CATCHING.get();
        if (!catching.isEmpty()) {
            catch_(catching, message);
            return;
        }
        if (consumer == null) {
            return;
        }
        emit(level, message, highlight, source());
    }

    private static void emit(Level level, String message, String highlight, ErrorSource source) {
        Deque<List<String>> catching = CATCHING.get();
        if (!catching.isEmpty()) {
            catch_(catching, message);
            return;
        }
        Consumer to = consumer;
        if (to == null) {
            return;
        }
        RuntimeError error = new RuntimeError(level,
                source == null ? ErrorSource.unknown("syntax", "-unknown-") : source, message, highlight);
        if ((level == Level.WARNING ? warningFrame : errorFrame).add(error)) {
            to.printError(error);
        }
    }

    private static void catch_(Deque<List<String>> catching, String message) {
        List<String> caught = catching.peek();
        if (caught.size() < CATCH_LIMIT) {
            caught.add(message);
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
