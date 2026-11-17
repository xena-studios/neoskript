/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The order Skript gives a script's problems in: the order it loads the script, not the order of its lines.
 * Skript reads every structure's header first, in the file's order (options, variables, aliases, {@code using}, and
 * headers it can't understand say so then). Then it sorts the structures by priority and runs each stage for all of
 * them in turn: functions' headers, then functions (priority 400), examples (550), events (600), and commands (1000)
 * last, as 2.17's command
 * structure has the default priority ({@code ScriptLoader.loadStructures}). Within a structure, problems come as its
 * lines do, except a command's, which Skript reads entry by entry: its entries, then the warnings about them, then
 * the trigger, then the usage ({@code SubCommandEntryData}).
 */
public final class MessageOrder {

    private MessageOrder() {}

    /** The stages Skript loads structures in. */
    private static final int INIT = 0;
    private static final int PRE_LOAD = 1;
    private static final int LOAD = 2;

    private static final int FUNCTION = 400;
    private static final int EXAMPLE = 550;
    private static final int EVENT = 600;
    private static final int DEFAULT = 1000;

    /**
     * Sorts one script's problems as Skript gives them. Problems with the file's layout ({@code layout}) come first, in
     * the order they were found, as Skript finds them reading the file.
     */
    public static List<Diagnostic> sort(List<Diagnostic> diagnostics, SourceFile file, Predicate<Diagnostic> layout) {
        Structures structures = new Structures(file);
        record Keyed(Diagnostic diagnostic, int index, long[] key) {}
        List<Keyed> keyed = new ArrayList<>();
        for (int i = 0; i < diagnostics.size(); i++) {
            Diagnostic diagnostic = diagnostics.get(i);
            keyed.add(new Keyed(diagnostic, i, layout.test(diagnostic) ? new long[] {0, 0, 0, 0, 0, 0}
                    : structures.key(diagnostic)));
        }
        keyed.sort(Comparator.<Keyed, long[]>comparing(Keyed::key, java.util.Arrays::compare)
                .thenComparingInt(Keyed::index));
        return keyed.stream().map(Keyed::diagnostic).toList();
    }

    /** The script's top-level structures, by the lines they span. */
    private static final class Structures {
        private final List<String> lines;
        /** For each line (0-based), the 0-based line of the structure it's in, or -1. */
        private final int[] header;
        /** Each structure's place in the file. */
        private final int[] index;

        Structures(SourceFile file) {
            lines = file.lines();
            header = new int[lines.size()];
            index = new int[lines.size()];
            int current = -1;
            int count = 0;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String text = line.strip();
                if (!text.isEmpty() && !text.startsWith("#") && indent(line) == 0) {
                    current = i;
                    index[i] = count++;
                }
                header[i] = current;
            }
        }

        private static int indent(String line) {
            int n = 0;
            while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
                n++;
            }
            return n;
        }

        /** Stage, priority, structure, then within the structure: its rank, line. */
        long[] key(Diagnostic diagnostic) {
            int at = diagnostic.line() - 1;
            if (at < 0 || at >= lines.size() || header[at] < 0) {
                return new long[] {1, INIT, 0, 0, 0, diagnostic.line()};
            }
            int head = header[at];
            long structure = index[head];
            String kind = lines.get(head).strip().toLowerCase(Locale.ROOT);
            boolean onHeader = at == head;
            if (diagnostic.message().startsWith("Can't understand this structure: ")) {
                return new long[] {1, INIT, 0, structure, 0, diagnostic.line()};
            }
            if (kind.startsWith("variables") || kind.startsWith("aliases")) {
                // Skript reads every line as an entry first, then each entry.
                return new long[] {1, INIT, 0, structure, diagnostic.message().endsWith("is not an entry (like 'name = value')")
                        ? 0 : 1, diagnostic.line()};
            }
            if (kind.startsWith("options") || kind.startsWith("using ") || kind.startsWith("aliases")
                    || kind.startsWith("auto reload")) {
                return new long[] {1, INIT, 0, structure, 0, diagnostic.line()};
            }
            if (kind.startsWith("function ") || kind.startsWith("local function ")) {
                return onHeader ? new long[] {1, PRE_LOAD, FUNCTION, structure, 0, diagnostic.line()}
                        : new long[] {1, LOAD, FUNCTION, structure, 0, diagnostic.line()};
            }
            if (kind.startsWith("command ")) {
                return new long[] {1, LOAD, DEFAULT, structure, commandRank(diagnostic, head, at), diagnostic.line()};
            }
            if (kind.startsWith("on ") || kind.startsWith("every ") || kind.startsWith("at ")) {
                // An event's header is read with the others; its code when events load.
                return onHeader ? new long[] {1, INIT, 0, structure, 0, diagnostic.line()}
                        : new long[] {1, LOAD, EVENT, structure, 0, diagnostic.line()};
            }
            // Other structures (an event without 'on', a test, an addon's) have the default priority; an example 550.
            return onHeader ? new long[] {1, INIT, 0, structure, 0, diagnostic.line()}
                    : new long[] {1, LOAD, kind.startsWith("example") ? EXAMPLE : isEventLike(kind) ? EVENT : DEFAULT,
                            structure, 0, diagnostic.line()};
        }

        /** An event written without 'on' ('join:'): anything that isn't another structure Skript has. */
        private static boolean isEventLike(String kind) {
            return !kind.startsWith("test ") && !kind.startsWith("parse") && !kind.startsWith("example")
                    && !kind.startsWith("aliases");
        }

        /**
         * Where a command's problem comes in the order Skript reads the command: its pattern, its entries, the
         * warnings about them, its trigger, its usage, its suggestions and subcommands, then what's checked last.
         */
        private int commandRank(Diagnostic diagnostic, int head, int at) {
            if (at == head) {
                String message = diagnostic.message();
                if (message.contains("'trigger' entry") || message.contains("optional subcommand")
                        || message.startsWith("A subcommand group") || message.startsWith("A command with the name /")) {
                    return 9;
                }
                // Its warnings come after its entries are read, the deprecated entry's last.
                return diagnostic.severity() != Severity.WARNING ? 0 : message.startsWith("[Deprecated]") ? 4 : 3;
            }
            // The command's own entry this line belongs to.
            int childIndent = -1;
            int entry = -1;
            for (int i = head + 1; i <= at; i++) {
                String line = lines.get(i);
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                if (childIndent < 0) {
                    childIndent = indent(line);
                }
                if (indent(line) == childIndent) {
                    entry = i;
                }
            }
            if (entry < 0) {
                return 1;
            }
            String key = lines.get(entry).strip().toLowerCase(Locale.ROOT);
            if (key.startsWith("trigger:")) {
                return 5;
            }
            if (key.startsWith("usage:")) {
                return 6;
            }
            if (key.startsWith("suggestions:") || key.startsWith("subcommand") || key.startsWith("subcommands:")) {
                return 7;
            }
            return 1;
        }
    }
}
