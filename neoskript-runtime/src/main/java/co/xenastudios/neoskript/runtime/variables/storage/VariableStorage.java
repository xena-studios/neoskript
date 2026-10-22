/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Saves global variables to a {@code variables.csv} in Skript's format, and loads them back. Variables
 * whose names start with {@code -} are kept in memory only, as in Skript. Saving writes a temporary file
 * and swaps it in, so a crash mid-save can't leave a half-written file.
 */
public final class VariableStorage {

    /** What a load or save did. */
    public record Result(int variables, int raw, int unsaved) {}

    private final VariableStore store;
    private final VariableCodecs codecs;
    private final Path file;
    private long savedAt = -1;

    public VariableStorage(VariableStore store, VariableCodecs codecs, Path file) {
        this.store = store;
        this.codecs = codecs;
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Loads the file, if there is one, into the store. */
    public Result load() throws IOException {
        if (!Files.exists(file)) {
            savedAt = store.modifications();
            return new Result(0, 0, 0);
        }
        int raw = 0;
        Map<String, VariableFile.Entry> entries = VariableFile.read(file);
        for (VariableFile.Entry entry : entries.values()) {
            Object value = codecs.decode(entry.type(), entry.bytes());
            if (value instanceof RawValue) {
                raw++;
            }
            store.set(entry.name(), value);
        }
        savedAt = store.modifications();
        return new Result(entries.size(), raw, 0);
    }

    /** Whether variables changed since they were last loaded or saved. */
    public boolean changed() {
        return store.modifications() != savedAt;
    }

    /** Saves every variable except memory-only ones. Values whose type can't be saved are skipped. */
    public synchronized Result save() throws IOException {
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
