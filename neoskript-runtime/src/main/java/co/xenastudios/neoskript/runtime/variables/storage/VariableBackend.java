/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Where global variables are saved: Skript's {@code variables.csv} ({@link VariableStorage}, the default),
 * NeoSkript's own file ({@link NativeVariableStorage}), an SQL database ({@link SqlVariableStorage}), or several of
 * them by pattern ({@link Databases}). Saving is safe from any thread, one save at a time.
 */
public interface VariableBackend extends AutoCloseable {

    /** The file variables are saved in, or {@code null} if they aren't in a file (MySQL). */
    Path file();

    /** Where variables are saved, for the console. */
    default String describe() {
        Path file = file();
        return file == null ? "the database" : file.getFileName().toString();
    }

    /** Loads the saved variables into the store. Nothing is saved until this has succeeded. */
    VariableStorage.Result load() throws IOException;

    /** Whether {@link #load} succeeded, so the variables may be saved. */
    boolean loaded();

    /** Whether variables changed since they were last loaded or saved. */
    boolean changed();

    /** Saves the variables. */
    VariableStorage.Result save() throws IOException;

    /**
     * Replaces each value whose saving needs code {@code loader} loaded with its saved bytes, for an addon that's
     * being disabled, so later saves never run its code. See {@link VariableStorage#detach}.
     */
    VariableStorage.Detached detach(ClassLoader loader);

    /** What was last loaded or saved for {@code name} (in lower case), or {@code null} if nothing. */
    default VariableFile.Entry previous(String name) {
        return null;
    }

    /**
     * Copies what's saved to {@code backups/} beside the file, as Skript's {@code backup interval} does, and returns the
     * copy, or {@code null} if there's nothing to copy yet. Storage that isn't a file (MySQL) can't.
     */
    default Path backupNow() throws IOException {
        throw new IOException(describe() + " isn't a file, so it can't be backed up this way");
    }

    /**
     * Cuts off a {@link #backupNow} that's running, leaving no part of its copy behind, and stops later ones: for the
     * server's stop, so its last save and {@link #close} don't wait for a backup of a big file.
     */
    default void cancelBackup() {}

    /**
     * {@link #previous} for many names in a row, as when an addon is disabled: storage that would read a file for each
     * reads it once.
     */
    default java.util.function.Function<String, VariableFile.Entry> previousLookup() {
        return this::previous;
    }

    /** What the last load or saves did that the console should hear about; each is given once. */
    default java.util.List<String> notes() {
        return java.util.List.of();
    }

    /** Lets go of open files. The variables aren't saved; call {@link #save} first. */
    @Override
    default void close() throws IOException {}
}
