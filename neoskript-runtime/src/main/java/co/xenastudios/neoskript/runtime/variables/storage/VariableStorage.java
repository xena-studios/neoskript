/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Saves global variables to a {@code variables.csv} in Skript's format, and loads them back. Variables
 * whose names start with {@code -} are kept in memory only, as in Skript. Saving writes a temporary file
 * and swaps it in, so a crash mid-save can't leave a half-written file.
 *
 * <p>Nothing is saved until {@link #load} has succeeded, so a file that couldn't be read is never written over
 * with fewer variables. When some lines couldn't be read, the file is first copied to {@code backups/}.
 */
public final class VariableStorage implements VariableBackend {

    /**
     * What a load or save did: the lines skipped, where the file was backed up because of them, and how many
     * temporary files an interrupted save left were removed.
     */
    public record Result(int variables, int raw, int unsaved, List<VariableFile.Problem> skipped, Path backup,
            int leftovers, List<String> keptPrevious, List<String> notSaved) {

        public Result(int variables, int raw, int unsaved, List<VariableFile.Problem> skipped, Path backup,
                int leftovers) {
            this(variables, raw, unsaved, skipped, backup, leftovers, List.of(), List.of());
        }

        public Result(int variables, int raw, int unsaved) {
            this(variables, raw, unsaved, List.of(), null, 0);
        }
    }

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final VariableStore store;
    private final VariableStore loadInto;
    private final java.util.function.Predicate<String> owns;
    private final VariableCodecs codecs;
    private final Path file;
    private volatile boolean loaded;
    private long savedAt = -1;

    public VariableStorage(VariableStore store, VariableCodecs codecs, Path file) {
        this(store, codecs, file, Routing.ALL);
    }

    /**
     * One of several databases: only the variables {@code routing} gives it are saved, and loading puts the
     * variables into {@link Routing#loadInto}, or into {@code store} if that's {@code null}.
     */
    public VariableStorage(VariableStore store, VariableCodecs codecs, Path file, Routing routing) {
        this.store = store;
        this.loadInto = routing.loadInto() != null ? routing.loadInto() : store;
        this.owns = routing.owns();
        this.codecs = codecs;
        this.file = file;
    }

    @Override
    public Path file() {
        return file;
    }

    /** Whether {@link #load} succeeded, so the variables may be saved. */
    @Override
    public boolean loaded() {
        return loaded;
    }

    /**
     * Loads the file, if there is one, into the store. Lines that can't be read are skipped, and the file is
     * backed up before anything can write over it. If the file can't be read, or the backup can't be made, this
     * throws and the variables are never saved.
     */
    @Override
    public synchronized Result load() throws IOException {
        loaded = false;
        int leftovers = removeLeftovers() + removeBackupLeftovers(file);
        if (!Files.exists(file)) {
            savedAt = store.modifications();
            loaded = true;
            return new Result(0, 0, 0, List.of(), null, leftovers);
        }
        VariableFile.Contents contents = VariableFile.load(file);
        List<VariableFile.Problem> skipped = new ArrayList<>(contents.problems());
        List<Map.Entry<String, Object>> values = new ArrayList<>(contents.entries().size());
        int raw = 0;
        // Each entry's bytes are let go once decoded, so a big file never needs its bytes and its values in full.
        for (java.util.Iterator<VariableFile.Entry> each = contents.entries().values().iterator(); each.hasNext(); ) {
            VariableFile.Entry entry = each.next();
            each.remove();
            Object value;
            try {
                value = codecs.decode(entry.type(), entry.bytes());
            } catch (RuntimeException | LinkageError e) {
                skipped.add(new VariableFile.Problem(0, entry.name(), entry.name(),
                        "variable " + entry.name() + " could not be loaded: " + e));
                continue;
            }
            if (value instanceof RawValue) {
                raw++;
            }
            values.add(Map.entry(entry.name(), value));
        }
        Path backup = skipped.isEmpty() ? null : backup(file);
        for (Map.Entry<String, Object> value : values) {
            loadInto.set(value.getKey(), value.getValue());
        }
        savedAt = store.modifications();
        loaded = true;
        return new Result(values.size(), raw, 0, List.copyOf(skipped), backup, leftovers);
    }

    /**
     * Copies {@code file} to {@code backups/<name>_<time>.<extension>} beside it, as Skript's backups are named,
     * and returns the copy, synced to disk.
     */
    public static Path backup(Path file) throws IOException {
        return backup(file, "");
    }

    /** {@link #backup(Path)}, with {@code tag} after the time in the name. */
    static Path backup(Path file, String tag) throws IOException {
        return backup(file, tag, target -> Durable.copy(file, target));
    }

    /** How a backup is written to the name it's given; {@link FileAlreadyExistsException} if it's taken. */
    interface Copier {
        Path copyTo(Path target) throws IOException;
    }

    /** A backup of {@code file} in {@code backups/}, named for now and {@code tag}, written by {@code copier}. */
    static Path backup(Path file, String tag, Copier copier) throws IOException {
        Path dir = file.toAbsolutePath().getParent().resolve("backups");
        Files.createDirectories(dir);
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot);
        String time = LocalDateTime.now().format(BACKUP_TIME);
        for (int n = 1; ; n++) {
            Path target = dir.resolve(base + "_" + time + (n == 1 ? "" : "-" + n) + tag + extension);
            try {
                // Synced, with its directory, so a step that relies on the backup can't outlive it.
                return copier.copyTo(target);
            } catch (FileAlreadyExistsException taken) {
                // Two backups in the same second: number the next.
            }
        }
    }

    /**
     * Not under this storage's lock: a save replaces the file in one step, so the channel opened here reads one whole
     * file however long the copy takes, and saves, the server's last one included, don't wait for it. Except on
     * Windows, where a rename over a file that's open can be refused: there the copy holds the lock, as it used to.
     */
    @Override
    public Path backupNow() throws IOException {
        if (Durable.WINDOWS) {
            // On Windows a save's rename over the file can be refused while the copy has it open, so a save waits
            // for the copy there, as before; stopping the server cancels the copy, so its last save doesn't wait long.
            synchronized (this) {
                return copyForBackup();
            }
        }
        return copyForBackup();
    }

    private Path copyForBackup() throws IOException {
        if (backupCancelled) {
            return null;
        }
        FileChannel in;
        try {
            in = FileChannel.open(file, java.nio.file.StandardOpenOption.READ);
        } catch (java.nio.file.NoSuchFileException notYet) {
            return null;
        }
        try (in) {
            java.nio.file.attribute.FileTime modified = Files.getLastModifiedTime(file);
            long size = in.size();
            return backup(file, SCHEDULED, target -> Durable.copy(in, null, size, target, () -> backupCancelled,
                    modified));
        }
    }

    @Override
    public void cancelBackup() {
        backupCancelled = true;
    }

    private volatile boolean backupCancelled;

    /**
     * Gzips a scheduled backup, as Skript's {@code compress backups} does, at its best compression, into
     * {@code <backup>.gz}, written as {@link Durable#copy} writes a copy ({@link Durable#gzip}), then removes the
     * uncompressed backup. Returns the compressed backup, and the problem if the copy couldn't be removed. It reads only the backup, so it holds no lock on the
     * variables; once {@code cancelled} is true, as when the server stops, it stops. If it fails, the uncompressed
     * backup stays.
     */
    public static Compressed compress(Path backup, java.util.function.BooleanSupplier cancelled) throws IOException {
        Path compressed = Durable.gzip(backup, backup.resolveSibling(backup.getFileName() + ".gz"), cancelled);
        try {
            Durable.delete(backup);
        } catch (IOException e) {
            // The compressed backup is whole and stays; the copy beside it counts as the same backup for
            // backups-to-keep, and goes with it.
            return new Compressed(compressed, e);
        }
        return new Compressed(compressed, null);
    }

    /** A compressed backup, and why the uncompressed copy couldn't be removed, or {@code null} if it was. */
    public record Compressed(Path file, IOException copyLeft) {}

    /**
     * What a scheduled backup's name has after its time ({@code variables_<time>_scheduled.csv}), so removing old ones
     * ({@link #purgeBackups}) never matches the backups NeoSkript makes to keep something safe.
     */
    static final String SCHEDULED = "_scheduled";

    /**
     * Removes all but the newest {@code keep} scheduled backups of {@code file} in {@code backups/}, as Skript's
     * {@code backups to keep}. Unlike Skript, only this file's scheduled backups
     * ({@code <name>_<time>_scheduled.<extension>}) are counted and removed: the backups NeoSkript makes to keep
     * something safe (lines it couldn't read, a recovery, the copy from Skript), other databases' backups and the native
     * storage's snapshots are never removed. Returns how many were removed.
     */
    public static int purgeBackups(Path file, int keep) throws IOException {
        Path dir = file.toAbsolutePath().getParent().resolve("backups");
        if (keep < 0 || !Files.isDirectory(dir)) {
            return 0;
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot);
        java.util.regex.Pattern shape = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(base)
                + "_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}(-\\d+)?" + java.util.regex.Pattern.quote(SCHEDULED + extension)
                // Compressed or not ({@link #compress}).
                + "(\\.gz)?");
        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> found = Files.newDirectoryStream(dir,
                path -> shape.matcher(path.getFileName().toString()).matches() && Files.isRegularFile(path))) {
            found.forEach(backups::add);
        }
        // Oldest first: the time in the name, then the number of a second backup in the same second.
        backups.sort(java.util.Comparator.comparing((Path p) -> {
            String n = p.getFileName().toString();
            return n.substring(base.length() + 1, base.length() + 20);
        }).thenComparing(p -> p.getFileName().toString().length()).thenComparing(p -> p.getFileName().toString()));
        // A backup and its compressed copy ('x' and 'x.gz', when removing the copy after compressing failed) are one.
        java.util.Map<String, List<Path>> byBackup = new java.util.LinkedHashMap<>();
        for (Path backup : backups) {
            String n = backup.getFileName().toString();
            byBackup.computeIfAbsent(n.endsWith(".gz") ? n.substring(0, n.length() - 3) : n, k -> new ArrayList<>())
                    .add(backup);
        }
        List<List<Path>> ordered = new ArrayList<>(byBackup.values());
        int removed = 0;
        for (int i = 0; i < ordered.size() - keep; i++) {
            for (Path backup : ordered.get(i)) {
                Durable.delete(backup);
            }
            removed++;
        }
        return removed;
    }

    /**
     * Removes the temporary files an interrupted save left beside the file. A save only replaces the file once
     * its temporary file is complete, so they're never needed while the file is there. If it isn't, they're kept.
     */
    private int removeLeftovers() throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        if (!Files.exists(file) || !Files.isDirectory(dir)) {
            return 0;
        }
        String prefix = file.getFileName().toString();
        int removed = 0;
        try (DirectoryStream<Path> temps = Files.newDirectoryStream(dir, path -> {
            String name = path.getFileName().toString();
            return name.startsWith(prefix) && name.endsWith(".tmp") && name.length() > prefix.length() + 4
                    && Files.isRegularFile(path);
        })) {
            for (Path temp : temps) {
                Files.deleteIfExists(temp);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Removes the temporary files a backup or snapshot copy left in {@code backups/} and {@code backups/snapshots/}
     * beside {@code file} when the server stopped in the middle of one. A copy is only renamed to its backup name
     * once it's whole, so they're never a backup. Only names of exactly the shape a copy's temporary file has are
     * removed: a backup's or snapshot's name ({@code variables_2026-11-17_01-02-03.csv}, {@code
     * variables_2026-11-17_01-02-03_scheduled.csv}, {@code variables_2026-11-17_01-02-03-004.nsv}), then
     * {@code .copying.tmp} ({@link Durable#temporary}), or {@code .gz.copying.tmp} for a backup being compressed
     * ({@link #compress}), or, from versions before it, the digits the system added and {@code .tmp}. A backup's name with just {@code .tmp} after may be an owner's own copy, and is kept. How many were
     * removed.
     */
    public static int removeBackupLeftovers(Path file) throws IOException {
        Path backups = file.toAbsolutePath().getParent().resolve("backups");
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        // A backup's and a snapshot's name end in the file's own extension, exactly: an owner's 'variables_<time>.bak2.tmp'
        // isn't one.
        java.util.regex.Pattern shape = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(
                dot < 0 ? name : name.substring(0, dot))
                + "_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}(-\\d{3})?([-_]\\d+)?(" + java.util.regex.Pattern.quote(SCHEDULED)
                + ")?" + java.util.regex.Pattern.quote(dot < 0 ? "" : name.substring(dot)) + "(\\d+\\.tmp|(\\.gz)?"
                + java.util.regex.Pattern.quote(Durable.TEMPORARY) + ")");
        int removed = 0;
        for (Path dir : List.of(backups, backups.resolve("snapshots"))) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> temps = Files.newDirectoryStream(dir, path -> {
                return shape.matcher(path.getFileName().toString()).matches() && Files.isRegularFile(path);
            })) {
                for (Path temp : temps) {
                    Files.deleteIfExists(temp);
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Whether variables changed since they were last loaded or saved. Never, if they didn't load. */
    @Override
    public boolean changed() {
        return loaded && store.modifications() != savedAt;
    }

    /**
     * Replaces each value whose saving needs code {@code loader} loaded (its class, or the codec registered for it)
     * with its saved bytes, as a {@link RawValue}, for an addon that's being disabled: later saves then never run
     * its code, whose classes go with it. The addon's codecs are then dropped. No value is ever deleted: one whose
     * codec fails now keeps the bytes it has in the file; one with none there stays as it is, and isn't saved.
     *
     * @return the variables kept as saved bytes, and those that couldn't be and won't be saved
     */
    @Override
    public synchronized Detached detach(ClassLoader loader) {
        java.util.Map<String, Object> found = new java.util.LinkedHashMap<>();
        store.forEach((name, value) -> {
            if (codecs.usesCodeOf(value, loader)) {
                found.put(name, value);
            }
        });
        List<String> kept = new ArrayList<>();
        List<String> notSaved = new ArrayList<>();
        Map<String, VariableFile.Entry> previous = null;
        for (java.util.Map.Entry<String, Object> entry : found.entrySet()) {
            VariableCodecs.Encoded encoded;
            try {
                encoded = codecs.encode(entry.getValue());
            } catch (RuntimeException | LinkageError e) {
                encoded = null;
            }
            if (encoded == null) {
                // What the file has for it, if anything.
                if (previous == null) {
                    previous = previousEntries();
                }
                VariableFile.Entry before = previous.get(entry.getKey().toLowerCase(Locale.ROOT));
                if (before == null) {
                    notSaved.add(entry.getKey());
                    continue;
                }
                encoded = new VariableCodecs.Encoded(before.type(), before.bytes());
            }
            store.set(entry.getKey(), new RawValue(encoded.type(), encoded.bytes()));
            kept.add(entry.getKey());
        }
        codecs.forget(loader);
        return new Detached(List.copyOf(kept), List.copyOf(notSaved));
    }

    /**
     * What {@link #detach} did.
     *
     * @param kept the variables now kept as their saved bytes
     * @param notSaved those whose value couldn't be encoded and that the file has nothing for: they keep their value
     *     in memory, but aren't saved
     */
    public record Detached(List<String> kept, List<String> notSaved) {}

    @Override
    public VariableFile.Entry previous(String name) {
        return previousEntries().get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public java.util.function.Function<String, VariableFile.Entry> previousLookup() {
        java.util.concurrent.atomic.AtomicReference<Map<String, VariableFile.Entry>> read =
                new java.util.concurrent.atomic.AtomicReference<>();
        return name -> read.updateAndGet(m -> m != null ? m : previousEntries()).get(name.toLowerCase(Locale.ROOT));
    }

    /** The entries the file has now, or none if it can't be read. */
    private Map<String, VariableFile.Entry> previousEntries() {
        try {
            return Files.exists(file) ? VariableFile.read(file) : Map.of();
        } catch (IOException | RuntimeException unreadable) {
            return Map.of();
        }
    }

    /** Saves every variable except memory-only ones, once they have loaded. Values whose type can't be saved are skipped. */
    @Override
    public synchronized Result save() throws IOException {
        if (!loaded) {
            // The store may be missing variables the file has.
            throw new IllegalStateException("The variables in " + file + " weren't loaded, so they aren't saved");
        }
        long modifications = store.modifications();
        List<VariableFile.Entry> entries = new ArrayList<>();
        int[] counts = new int[2];
        List<String> broken = new ArrayList<>();
        store.forEach((name, value) -> {
            if (name.startsWith("-") || !owns.test(name)) {
                return;
            }
            VariableCodecs.Encoded encoded;
            try {
                encoded = codecs.encode(value);
            } catch (RuntimeException | LinkageError failed) {
                // A codec that fails, or whose classes are gone: this variable keeps what was saved before, and the
                // rest are saved as usual.
                broken.add(name.toLowerCase(Locale.ROOT));
                return;
            }
            if (encoded == null) {
                counts[1]++;
                return;
            }
            if (value instanceof RawValue) {
                counts[0]++;
            }
            entries.add(new VariableFile.Entry(name.toLowerCase(Locale.ROOT), encoded.type(), encoded.bytes()));
        });
        List<String> kept = new ArrayList<>();
        List<String> notSaved = new ArrayList<>();
        if (!broken.isEmpty()) {
            Map<String, VariableFile.Entry> previous = previousEntries();
            for (String name : broken) {
                VariableFile.Entry before = previous.get(name);
                if (before != null) {
                    entries.add(before);
                    kept.add(name);
                } else {
                    notSaved.add(name);
                }
            }
        }
        VariableFile.write(file, entries);
        savedAt = modifications;
        return new Result(entries.size(), counts[0], counts[1], List.of(), null, 0, List.copyOf(kept),
                List.copyOf(notSaved));
    }
}
