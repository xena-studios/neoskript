/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Where global variables are saved: Skript's {@code variables.csv} ({@link VariableStorage}, the default) or
 * NeoSkript's own file ({@link NativeVariableStorage}). Saving is safe from any thread, one save at a time.
 */
public interface VariableBackend extends AutoCloseable {

    /** The file variables are saved in. */
    Path file();

    /** Loads the saved variables into the store. Nothing is saved until this has succeeded. */
    VariableStorage.Result load() throws IOException;

    /** Whether {@link #load} succeeded, so the variables may be saved. */
    boolean loaded();

    /** Whether variables changed since they were last loaded or saved. */
    boolean changed();

    /** Saves the variables. */
    VariableStorage.Result save() throws IOException;

    /** Lets go of open files. The variables aren't saved; call {@link #save} first. */
    @Override
    default void close() throws IOException {}
}
