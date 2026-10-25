/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.errors;

import co.xenastudios.neoskript.runtime.errors.ErrorSource.Location;
import co.xenastudios.neoskript.runtime.errors.RuntimeError.Level;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What Skript's {@code BukkitRuntimeErrorConsumer} prints, in its English messages ({@code log.runtime.*} and
 * {@code skript command.reload.*details}) with their colour tags. Messages are made with {@code \n} between
 * lines; the console gets a line at a time.
 */
public final class RuntimeErrorMessages {

    private RuntimeErrorMessages() {}

    static final String ERROR_INFO = "<light red>The script '<gray>%s<light red>' encountered an error while executing the"
            + " '<gray>%s<light red>' %s<light red>:\n";
    static final String WARNING_INFO = "<yellow>The script '<gray>%s<yellow>' encountered a warning while executing the"
            + " '<gray>%s<yellow>' %s<yellow>:\n";
    static final String ERROR_DETAILS = "<light red>    %s<reset>\n";
    static final String WARNING_DETAILS = "<yellow>    %s<reset>\n";
    static final String LINE_INFO = "<gold>    Line %s<white>: <gray>%s\n \n";

    static final String ERRORS_SKIPPED = "<gold>%d<light red> runtime error[s] were thrown but not printed in the last"
            + " frame. From: <gray>%s";
    static final String ERRORS_TIMED_OUT = "<gold>Line %d<light red> of script '<gray>%s<light red>' produced too many"
            + " runtime errors (<gray>%d<light red> allowed per frame). No errors from this line will be printed for %d"
            + " frames.";
    static final String ERROR_NOTIFICATION = "<light red>Script '<gray>%s<light red>' produced runtime errors. Please"
            + " check console logs for details.";
    static final String ERROR_NOTIFICATION_PLURAL = "<light red>Script '<gray>%s<light red>' and %d others produced"
            + " runtime errors. Please check console logs for details.";

    static final String WARNINGS_SKIPPED = "<yellow>%d<yellow> runtime warning[s] were thrown but not printed in the"
            + " last frame. From: <gray>%s";
    static final String WARNINGS_TIMED_OUT = "<gold>Line %d<yellow> of script '<gray>%s<yellow>' produced too many"
            + " runtime warnings (<gray>%d<yellow> allowed per frame). No warnings from this line will be printed for %d"
            + " frames.";
    static final String WARNING_NOTIFICATION = "<yellow>Script '<gray>%s<yellow>' produced runtime warnings. Please"
            + " check console logs for details.";
    static final String WARNING_NOTIFICATION_PLURAL = "<gold>Script '<gray>%s<yellow>' and %d others produced runtime"
            + " warnings. Please check console logs for details.";

    /** An error as the console shows it: the script and syntax, the message, and the line with its code. */
    public static String error(RuntimeError error) {
        boolean warning = error.level() == Level.WARNING;
        ErrorSource source = error.source();
        String code = source.lineText();
        if (error.toHighlight() != null && !error.toHighlight().isEmpty()) {
            code = code.replace("§", "&").replaceFirst(error.toHighlight(), "§f§n$0§7");
        }
        return String.format(warning ? WARNING_INFO : ERROR_INFO, source.script(), source.syntaxName(), source.syntaxType())
                + String.format(warning ? WARNING_DETAILS : ERROR_DETAILS, error.error().replace("§", "&"))
                + String.format(LINE_INFO, source.lineNumber(), code);
    }

    /** The end of a frame in which errors weren't printed: how many, and from which lines; else null. */
    public static String skipped(ErrorFrame.Output output, Level level) {
        if (output.skippedErrors().isEmpty()) {
            return null;
        }
        String lines = output.skippedErrors().entrySet().stream()
                .map(entry -> "'" + entry.getKey().script() + "' line " + entry.getKey().lineNumber() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
        int skipped = output.skippedErrors().values().stream().reduce(0, Integer::sum);
        return String.format(level == Level.ERROR ? ERRORS_SKIPPED : WARNINGS_SKIPPED, skipped, lines);
    }

    /** That a line was put in timeout in this frame. */
    public static String timedOut(Location location, ErrorFrame.Limits limits, Level level) {
        return String.format(level == Level.ERROR ? ERRORS_TIMED_OUT : WARNINGS_TIMED_OUT, location.lineNumber(),
                location.script(), limits.lineTimeoutLimit(), limits.timeoutDuration());
    }

    /** What players who may see runtime errors are told when a frame had any; else null. */
    public static String notification(ErrorFrame.Output output, Level level) {
        Map<Location, Integer> totals = output.totalErrors();
        if (totals.isEmpty()) {
            return null;
        }
        Set<String> scripts = new LinkedHashSet<>();
        totals.keySet().forEach(location -> scripts.add(location.script()));
        String message = level == Level.ERROR
                ? (scripts.size() > 1 ? ERROR_NOTIFICATION_PLURAL : ERROR_NOTIFICATION)
                : (scripts.size() > 1 ? WARNING_NOTIFICATION_PLURAL : WARNING_NOTIFICATION);
        return String.format(message, scripts.iterator().next(), scripts.size() - 1);
    }
}
