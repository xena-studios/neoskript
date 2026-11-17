/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An addon's values in variables, when it's disabled and its classes go. */
class VariableStorageDetachTest {

    /** A type of an addon's: this test's class loader stands in for the addon's. */
    record Ticket(long number) {}

    @TempDir
    Path dir;

    private final VariableStore store = new VariableStore();
    private final VariableCodecs codecs = new VariableCodecs();
    /** Whether the addon is gone, so its codec's classes can't load. */
    private final AtomicBoolean gone = new AtomicBoolean();

    private VariableStorage storage() throws Exception {
        codecs.register("string", String.class, new VariableCodec<>() {
            @Override
            public void encode(String value, BinaryWriter out) {
                out.rawString(value);
            }

            @Override
            public String decode(BinaryReader in) {
                return in.rawString();
            }
        });
        codecs.register("test:ticket", Ticket.class, new VariableCodec<>() {
            @Override
            public void encode(Ticket value, BinaryWriter out) {
                if (gone.get()) {
                    throw new NoClassDefFoundError("the addon's classes are gone");
                }
                out.rawLong(value.number());
            }

            @Override
            public Ticket decode(BinaryReader in) {
                return new Ticket(in.rawLong());
            }
        });
        Path file = dir.resolve("variables.csv");
        Files.writeString(file, "", StandardCharsets.UTF_8);
        VariableStorage storage = new VariableStorage(store, codecs, file);
        storage.load();
        return storage;
    }

    @Test
    void detachedValuesAreSavedWithoutTheAddonsCode() throws Exception {
        VariableStorage storage = storage();
        store.set("ticket", new Ticket(7));
        store.set("text", "kept");
        assertEquals(List.of("ticket"), storage.detach(Ticket.class.getClassLoader()));
        assertInstanceOf(RawValue.class, store.get("ticket"));
        assertEquals("kept", store.get("text"));
        gone.set(true);
        // A change after the addon is gone, as an unload trigger makes, is saved with the ticket.
        store.set("stopped", "yes");
        storage.save();
        String saved = Files.readString(storage.file());
        assertTrue(saved.contains("ticket, test:ticket, "), saved);
        assertTrue(saved.contains("stopped, string, "), saved);
    }

    @Test
    void aSaveThatNeedsCodeThatsGoneNamesTheVariablesAndWritesNothing() throws Exception {
        VariableStorage storage = storage();
        store.set("text", "first");
        storage.save();
        String before = Files.readString(storage.file());
        store.set("ticket", new Ticket(7));
        gone.set(true);
        IllegalStateException failed = assertThrows(IllegalStateException.class, storage::save);
        assertTrue(failed.getMessage().contains("ticket"), failed.getMessage());
        assertEquals(before, Files.readString(storage.file()));
    }
}
