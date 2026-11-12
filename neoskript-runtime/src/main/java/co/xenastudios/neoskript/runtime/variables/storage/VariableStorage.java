/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
public final class VariableStorage {

    /**
     * What a load or save did: the lines skipped, where the file was backed up because of them, and how many
     * temporary files an interrupted save left were removed.
     */
    public record Result(int variables, int raw, int unsaved, List<VariableFile.Problem> skipped, Path backup,
            int leftovers) {

        public Result(int variables, int raw, int unsaved) {
            this(variables, raw, unsaved, List.of(), null, 0);
        }
    }

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final VariableStore store;
    private final VariableCodecs codecs;
    private final Path file;
    private volatile boolean loaded;
    private long savedAt = -1;

    public VariableStorage(VariableStore store, VariableCodecs codecs, Path file) {
        this.store = store;
        this.codecs = codecs;
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Whether {@link #load} succeeded, so the variables may be saved. */
    public boolean loaded() {
        return loaded;
    }

    /**
     * Loads the file, if there is one, into the store. Lines that can't be read are skipped, and the file is
     * backed up before anything can write over it. If the file can't be read, or the backup can't be made, this
     * throws and the variables are never saved.
     */
    public synchronized Result load() throws IOException {
        loaded = false;
        int leftovers = removeLeftovers();
        if (!Files.exists(file)) {
            savedAt = store.modifications();
            loaded = true;
            return new Result(0, 0, 0, List.of(), null, leftovers);
        }
        VariableFile.Contents contents = VariableFile.load(file);
        List<VariableFile.Problem> skipped = new ArrayList<>(contents.problems());
        List<Map.Entry<String, Object>> values = new ArrayList<>(contents.entries().size());
        int raw = 0;
        for (VariableFile.Entry entry : contents.entries().values()) {
            Object value;
            try {
                value = codecs.decode(entry.type(), entry.bytes());
            } catch (RuntimeException e) {
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
            store.set(value.getKey(), value.getValue());
        }
        savedAt = store.modifications();
        loaded = true;
        return new Result(values.size(), raw, 0, List.copyOf(skipped), backup, leftovers);
    }

    /**
     * Copies {@code file} to {@code backups/<name>_<time>.<extension>} beside it, as Skript's backups are named,
     * and returns the copy.
     */
    public static Path backup(Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent().resolve("backups");
        Files.createDirectories(dir);
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot);
        String time = LocalDateTime.now().format(BACKUP_TIME);
        for (int n = 1; ; n++) {
            Path target = dir.resolve(base + "_" + time + (n == 1 ? "" : "-" + n) + extension);
            try {
                return Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (FileAlreadyExistsException taken) {
                // Two backups in the same second: number the next.
            }
        }
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

    /** Whether variables changed since they were last loaded or saved. Never, if they didn't load. */
    public boolean changed() {
        return loaded && store.modifications() != savedAt;
    }

    /** Saves every variable except memory-only ones, once they have loaded. Values whose type can't be saved are skipped. */
    public synchronized Result save() throws IOException {
        if (!loaded) {
            // The store may be missing variables the file has.
            throw new IllegalStateException("The variables in " + file + " weren't loaded, so they aren't saved");
        }
        long modifications = store.modifications();
        List<VariableFile.Entry> entries = new ArrayList<>();
        int[] counts = new int[2];
        store.forEach((name, value) -> {
            if (name.startsWith("-")) {
                return;
            }
            VariableCodecs.Encoded encoded = codecs.encode(value);
            if (encoded == null) {
                counts[1]++;
                return;
            }
            if (value instanceof RawValue) {
                counts[0]++;
            }
            entries.add(new VariableFile.Entry(name.toLowerCase(Locale.ROOT), encoded.type(), encoded.bytes()));
        });
        VariableFile.write(file, entries);
        savedAt = modifications;
        return new Result(entries.size(), counts[0], counts[1]);
    }
}
