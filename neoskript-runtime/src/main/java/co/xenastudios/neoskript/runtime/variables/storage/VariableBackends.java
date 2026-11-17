/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Picks where variables are saved ({@code variables.storage} in config.yml) and moves them over when that
 * changed. Exactly one file holds the variables: while NeoSkript's own file ({@code variables.nsv}) exists, it's
 * the one in use.
 *
 * <ul>
 *   <li>{@code native} with no {@code variables.nsv} yet: {@code variables.csv} is backed up, copied into a new
 *       {@code variables.nsv}, and then removed, so it can't be read again by mistake.
 *   <li>{@code csv} with a {@code variables.nsv}: the variables are written to {@code variables.csv} (the old one
 *       backed up first) and {@code variables.nsv} is moved to {@code backups/}. Skript can read the result.
 * </ul>
 *
 * <p>Values are copied as their saved bytes, never decoded, so nothing NeoSkript can't read yet is lost. A step
 * that fails leaves the file it was reading where it was.
 */
public final class VariableBackends {

    /** Where variables are saved. */
    public enum Kind {
        CSV,
        NATIVE;

        /** {@code csv} or {@code native}, ignoring case and spaces; {@code null} if it's neither. */
        public static Kind parse(String text) {
            if (text == null) {
                return CSV;
            }
            return switch (text.strip().toLowerCase(Locale.ROOT)) {
                case "csv", "" -> CSV;
                case "native" -> NATIVE;
                default -> null;
            };
        }
    }

    /** The storage to load from, and what was done to get there, for the log. */
    public record Opened(VariableBackend backend, List<String> log) {}

    private VariableBackends() {}

    /** NeoSkript's own file beside {@code csv}: {@code variables.csv} becomes {@code variables.nsv}. */
    public static Path nativeFile(Path csv) {
        String name = csv.getFileName().toString();
        String base = name.toLowerCase(Locale.ROOT).endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
        return csv.resolveSibling(base + ".nsv");
    }

    /** The storage for {@code kind}, after moving the variables over if they're in the other file. */
    public static Opened open(Kind kind, VariableStore store, VariableCodecs codecs, Path csv) throws IOException {
        Path nsv = nativeFile(csv);
        List<String> log = new ArrayList<>();
        if (kind == Kind.NATIVE) {
            if (Files.exists(nsv)) {
                if (Files.exists(csv)) {
                    log.add("Variables are saved in " + nsv.getFileName() + ", so " + csv.getFileName()
                            + " beside it isn't read. Remove " + nsv.getFileName() + " to import it instead.");
                }
            } else if (Files.exists(csv)) {
                importCsv(csv, nsv, log);
            }
            return new Opened(new NativeVariableStorage(store, codecs, nsv), List.copyOf(log));
        }
        if (Files.exists(nsv)) {
            exportCsv(nsv, csv, log);
        }
        return new Opened(new VariableStorage(store, codecs, csv), List.copyOf(log));
    }

    /** Copies {@code csv} into a new {@code nsv}, backing it up first, then removes it. */
    static void importCsv(Path csv, Path nsv, List<String> log) throws IOException {
        Path backup = VariableStorage.backup(csv);
        VariableFile.Contents contents = VariableFile.load(csv);
        if (!contents.problems().isEmpty()) {
            for (VariableFile.Problem problem : contents.problems()) {
                log.add(problem.message());
            }
            log.add(contents.problems().size() + " lines of " + csv.getFileName()
                    + " couldn't be read and weren't imported; the backup keeps them.");
        }
        List<NativeVariableFile.Op> ops = inOrder(contents.entries().values());
        NativeVariableStorage.write(nsv, ops);
        Files.delete(csv);
        log.add("Moved " + ops.size() + " variables from " + csv.getFileName() + " to " + nsv.getFileName()
                + " (backed up as " + relative(csv, backup) + ")");
    }

    /** Writes the variables in {@code nsv} to {@code csv}, backing up both, then moves {@code nsv} to backups. */
    static void exportCsv(Path nsv, Path csv, List<String> log) throws IOException {
        NativeVariableFile.Contents contents = NativeVariableFile.read(Files.readAllBytes(nsv));
        if (Files.exists(csv)) {
            log.add("Backed up the old " + csv.getFileName() + " as " + relative(csv, VariableStorage.backup(csv)));
        }
        if (contents.damaged()) {
            log.add("The last " + (contents.length() - contents.validEnd()) + " bytes of " + nsv.getFileName()
                    + " were cut off or damaged and weren't exported; the backup keeps them.");
        }
        List<NativeVariableFile.Op> ops = inOrder(contents.entries().values());
        List<VariableFile.Entry> entries = new ArrayList<>(ops.size());
        for (NativeVariableFile.Op op : ops) {
            entries.add(new VariableFile.Entry(op.name(), op.type(), op.bytes()));
        }
        VariableFile.write(csv, entries);
        Path backup = VariableStorage.backup(nsv);
        Files.delete(nsv);
        log.add("Moved " + ops.size() + " variables from " + nsv.getFileName() + " back to " + csv.getFileName()
                + ", which Skript can read (" + nsv.getFileName() + " is kept as " + relative(csv, backup) + ")");
    }

    /**
     * The entries in the order NeoSkript saves them, which is Skript's: through a store of their saved bytes,
     * so names are merged and sorted exactly as when the variables are loaded and saved.
     */
    private static List<NativeVariableFile.Op> inOrder(Iterable<VariableFile.Entry> entries) {
        VariableStore sorted = new VariableStore();
        for (VariableFile.Entry entry : entries) {
            sorted.set(entry.name(), new RawValue(entry.type(), entry.bytes()));
        }
        List<NativeVariableFile.Op> ops = new ArrayList<>();
        sorted.forEach((name, value) -> {
            RawValue raw = (RawValue) value;
            ops.add(new NativeVariableFile.Op(name.toLowerCase(Locale.ROOT), raw.type(), raw.bytes()));
        });
        return ops;
    }

    private static String relative(Path beside, Path file) {
        Path dir = beside.toAbsolutePath().getParent();
        return dir.relativize(file.toAbsolutePath()).toString();
    }
}
