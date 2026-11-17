/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An addon's values in variables, when it's disabled and its classes go, or when its codec can't load. */
class VariableStorageDetachTest {

    /** Loads {@link AddonCodecs} and its nested classes itself, as an addon's jar's loader would. */
    private static final class AddonLoader extends ClassLoader {
        private static final String PREFIX = AddonCodecs.class.getName();

        AddonLoader() {
            super(VariableStorageDetachTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PREFIX)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        byte[] bytes = in.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException | NullPointerException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                return loaded;
            }
        }
    }

    @TempDir
    Path dir;

    private final AtomicBoolean gone = new AtomicBoolean();
    private final AtomicBoolean missingPlugin = new AtomicBoolean();
    private AddonLoader addon;
    private Class<?> addonCodecs;

    @BeforeEach
    void loadTheAddon() throws Exception {
        addon = new AddonLoader();
        addonCodecs = addon.loadClass(AddonCodecs.class.getName());
    }

    private Object ticket(long number) throws Exception {
        return addonCodecs.getMethod("ticket", long.class).invoke(null, number);
    }

    /** NeoSkript's codecs (here, the string one) and the addon's, over {@code store}, loaded from the file. */
    private VariableStorage storage(VariableStore store) throws Exception {
        VariableCodecs codecs = new VariableCodecs();
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
        addonCodecs.getMethod("register", VariableCodecs.class, AtomicBoolean.class, AtomicBoolean.class)
                .invoke(null, codecs, gone, missingPlugin);
        VariableStorage storage = new VariableStorage(store, codecs, dir.resolve("variables.csv"));
        storage.load();
        return storage;
    }

    private static String line(VariableStorage storage, String name) throws IOException {
        return Files.readAllLines(storage.file()).stream().filter(line -> line.startsWith(name + ", ")).findFirst()
                .orElse(null);
    }

    @Test
    void detachFindsValuesByTheirCodecsLoaderAndAShutdownSaveKeepsThem() throws Exception {
        VariableStore store = new VariableStore();
        VariableStorage storage = storage(store);
        store.set("ticket", ticket(7));
        // A JDK type the addon didn't load, saved by the addon's codec.
        store.set("wait", Duration.ofSeconds(3));
        store.set("text", "kept");
        assertEquals(List.of("ticket", "wait"), storage.detach(addon));
        assertInstanceOf(RawValue.class, store.get("ticket"));
        assertInstanceOf(RawValue.class, store.get("wait"));
        assertEquals("kept", store.get("text"));
        // The addon's jar closes; an unload trigger changes a variable; the shutdown save runs.
        gone.set(true);
        store.set("stopped", "yes");
        VariableStorage.Result saved = storage.save();
        assertEquals(List.of(), saved.keptPrevious());
        assertTrue(line(storage, "ticket").startsWith("ticket, test:ticket, "));
        assertTrue(line(storage, "wait").startsWith("wait, test:duration, "));
        assertTrue(line(storage, "stopped").startsWith("stopped, string, "));
    }

    @Test
    void anEntryThatCantBeEncodedKeepsItsOldBytesAndTheRestAreSaved() throws Exception {
        VariableStore store = new VariableStore();
        VariableStorage storage = storage(store);
        store.set("ticket", ticket(7));
        store.set("wait", Duration.ofSeconds(3));
        store.set("text", "first");
        storage.save();
        String ticketBefore = line(storage, "ticket");
        String waitBefore = line(storage, "wait");
        String textBefore = line(storage, "text");
        // The addon's classes go without a detach.
        gone.set(true);
        store.set("ticket", ticket(8));
        store.set("wait", Duration.ofSeconds(4));
        store.set("fresh", ticket(9));
        store.set("text", "second");
        VariableStorage.Result saved = storage.save();
        assertEquals(List.of("ticket", "wait"), saved.keptPrevious());
        assertEquals(List.of("fresh"), saved.notSaved());
        assertEquals(ticketBefore, line(storage, "ticket"));
        assertEquals(waitBefore, line(storage, "wait"));
        assertTrue(!textBefore.equals(line(storage, "text")), "text should have its new value");
    }

    @Test
    void aCodecThatCantLoadItsClassesKeepsTheBytesAndTheRestLoad() throws Exception {
        VariableStore first = new VariableStore();
        VariableStorage before = storage(first);
        first.set("ticket", ticket(7));
        first.set("text", "a");
        before.save();
        String ticketLine = line(before, "ticket");

        missingPlugin.set(true);
        VariableStore store = new VariableStore();
        VariableStorage storage = storage(store);
        assertEquals("a", store.get("text"));
        assertInstanceOf(RawValue.class, store.get("ticket"));
        storage.save();
        assertEquals(ticketLine, line(storage, "ticket"));
    }
}
