/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>Values are copied as their saved bytes, never decoded, so nothing NeoSkript can't read yet is lost. The file
 * read is only removed once the new file and the backup are synced to disk, and the removal is synced too, so a
 * power cut at any point leaves the variables in at least one whole file. When both files are found, see
 * {@link #open}.
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

    /** Both files hold different variables and only the owner can say which to keep. Neither was changed. */
    public static final class Conflict extends IOException {
        Conflict(String message) {
            super(message);
        }
    }

    /** The storage to load from, what was done to get there, and warnings, for the log. */
    public record Opened(VariableBackend backend, List<String> log, List<String> warnings) {}

    private VariableBackends() {}

    /** NeoSkript's own file beside {@code csv}: {@code variables.csv} becomes {@code variables.nsv}. */
    public static Path nativeFile(Path csv) {
        String name = csv.getFileName().toString();
        String base = name.toLowerCase(Locale.ROOT).endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
        return csv.resolveSibling(base + ".nsv");
    }

    /**
     * The storage for {@code kind}, after moving the variables over if they're in the other file. Throws, changing
     * neither file, if both files exist with different variables and {@code variables.csv} is the newer: something
     * that doesn't know about {@code variables.nsv} (an older NeoSkript, or Skript) saved it, and only the owner can
     * say which to keep.
     */
    public static Opened open(Kind kind, VariableStore store, VariableCodecs codecs, Path csv) throws IOException {
        return open(kind, store, codecs, csv, false);
    }

    /**
     * As {@link #open(Kind, VariableStore, VariableCodecs, Path)}, with {@code variables.recover}: a damaged
     * {@code variables.nsv} that has to be read to move or compare its variables is recovered (backed up, and the
     * saves before the damage kept) instead of refused. In native storage the native storage's own load does that.
     */
    public static Opened open(Kind kind, VariableStore store, VariableCodecs codecs, Path csv, boolean recover)
            throws IOException {
        return open(kind, store, codecs, csv, recover, Routing.ALL);
    }

    /** As {@link #open(Kind, VariableStore, VariableCodecs, Path, boolean)}, for one of several databases. */
    public static Opened open(Kind kind, VariableStore store, VariableCodecs codecs, Path csv, boolean recover,
            Routing routing) throws IOException {
        Path nsv = nativeFile(csv);
        List<String> log = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (Files.exists(nsv) && Files.exists(csv)) {
            both(kind, nsv, csv, recover, log);
        } else if (kind == Kind.NATIVE && Files.exists(csv)) {
            importCsv(csv, nsv, log);
        } else if (kind == Kind.CSV && Files.exists(nsv)) {
            exportCsv(nsv, csv, recover, log);
        }
        if (kind == Kind.NATIVE) {
            warnings.add("Variables are saved in " + nsv.getFileName() + " (variables.storage: native), which older "
                    + "NeoSkript versions and Skript don't read. Before downgrading NeoSkript or going back to Skript, "
                    + "set variables.storage to csv and restart once, so " + csv.getFileName() + " is written.");
            return new Opened(new NativeVariableStorage(store, codecs, nsv, routing), List.copyOf(log), List.copyOf(warnings));
        }
        return new Opened(new VariableStorage(store, codecs, csv, routing), List.copyOf(log), List.copyOf(warnings));
    }

    /**
     * Both files are there. With the same variables, a switch was cut off before it removed the file it read:
     * that's finished. Otherwise the newer file wins if it's {@code variables.nsv}, and the other is backed up.
     */
    private static void both(Kind kind, Path nsv, Path csv, boolean recover, List<String> log) throws IOException {
        // Both times first, before anything is written: a recovery below rewrites variables.nsv.
        Instant nativeTime = Files.getLastModifiedTime(nsv).toInstant();
        Instant csvTime = Files.getLastModifiedTime(csv).toInstant();
        String times = csv.getFileName() + " was last changed " + csvTime + ", " + nsv.getFileName() + " " + nativeTime;
        NativeVariableStorage.Opened read = NativeVariableStorage.open(nsv, recover, log);
        List<NativeVariableFile.Op> fromNative = inOrder(read.entries().values());
        List<NativeVariableFile.Op> fromCsv = inOrder(VariableFile.load(csv).entries().values());
        boolean same = same(fromNative, fromCsv);
        if (!same && csvTime.isAfter(nativeTime)) {
            throw new Conflict(csv.getFileName() + " and " + nsv.getFileName() + " are both there with different "
                    + "variables, and " + csv.getFileName() + " is newer, so something that doesn't read "
                    + nsv.getFileName() + " (an older NeoSkript, or Skript) saved it. NeoSkript won't pick one: move "
                    + "the one you don't want out of the folder and restart. " + times);
        }
        if (read.salvaged() != null && kind == Kind.NATIVE) {
            // Recovered: the new native file is written now, so loading it doesn't recover it again.
            NativeVariableStorage.Written written = NativeVariableStorage.write(nsv,
                    NativeVariableStorage.inOrder(read.salvaged().values()));
            if (written.problem() != null) {
                log.add(written.problem());
            }
        }
        if (same) {
            if (kind == Kind.NATIVE) {
                Path backup = VariableStorage.backup(csv);
                Durable.delete(csv);
                log.add("Finished moving to " + nsv.getFileName() + ": " + csv.getFileName() + " held the same variables"
                        + " and is now " + relative(csv, backup));
            } else {
                Path backup = VariableStorage.backup(nsv);
                Durable.delete(nsv);
                log.add("Finished moving back to " + csv.getFileName() + ": " + nsv.getFileName() + " held the same"
                        + " variables and is now " + relative(csv, backup));
            }
            return;
        }
        if (kind == Kind.NATIVE) {
            Path backup = VariableStorage.backup(csv);
            Durable.delete(csv);
            log.add(csv.getFileName() + " was older than " + nsv.getFileName() + ", which is in use, so it was moved to "
                    + relative(csv, backup) + " unread. " + times);
        } else {
            exportCsv(nsv, csv, read, log);
            log.add(times + " before the move.");
        }
    }

    private static boolean same(List<NativeVariableFile.Op> a, List<NativeVariableFile.Op> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            NativeVariableFile.Op x = a.get(i);
            NativeVariableFile.Op y = b.get(i);
            if (!x.name().equals(y.name()) || !x.type().equals(y.type()) || !Arrays.equals(x.bytes(), y.bytes())) {
                return false;
            }
        }
        return true;
    }

    /** Copies {@code csv} into a new {@code nsv}, backing it up first, then removes it once both are on disk. */
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
        NativeVariableStorage.Written written = NativeVariableStorage.write(nsv, ops);
        if (written.problem() != null) {
            log.add(written.problem());
        }
        Durable.delete(csv);
        log.add("Moved " + ops.size() + " variables from " + csv.getFileName() + " to " + nsv.getFileName()
                + " (backed up as " + relative(csv, backup) + ")");
    }

    /**
     * Writes the variables in {@code nsv} to {@code csv}, backing up both, then removes {@code nsv} once the new
     * {@code csv} and the backup are on disk.
     */
    static void exportCsv(Path nsv, Path csv, boolean recover, List<String> log) throws IOException {
        exportCsv(nsv, csv, NativeVariableStorage.open(nsv, recover, log), log);
    }

    private static void exportCsv(Path nsv, Path csv, NativeVariableStorage.Opened read, List<String> log)
            throws IOException {
        if (Files.exists(csv)) {
            log.add("Backed up the old " + csv.getFileName() + " as " + relative(csv, VariableStorage.backup(csv)));
        }
        if (read.contents() != null && read.contents().damaged()) {
            log.add("The last " + (read.contents().length() - read.contents().validEnd()) + " bytes of "
                    + nsv.getFileName() + " were a save cut off by a crash and weren't exported; the backup keeps them.");
        }
        List<NativeVariableFile.Op> ops = inOrder(read.entries().values());
        List<VariableFile.Entry> entries = new ArrayList<>(ops.size());
        for (NativeVariableFile.Op op : ops) {
            entries.add(new VariableFile.Entry(op.name(), op.type(), op.bytes()));
        }
        VariableFile.write(csv, entries);
        Path backup = VariableStorage.backup(nsv);
        Durable.delete(nsv);
        log.add("Moved " + ops.size() + " variables from " + nsv.getFileName() + " back to " + csv.getFileName()
                + ", which Skript can read (" + nsv.getFileName() + " is kept as " + relative(csv, backup) + ")");
    }

    private static List<NativeVariableFile.Op> inOrder(Iterable<VariableFile.Entry> entries) {
        return NativeVariableStorage.inOrder(entries);
    }

    private static String relative(Path beside, Path file) {
        Path dir = beside.toAbsolutePath().getParent();
        return dir.relativize(file.toAbsolutePath()).toString();
    }
}
