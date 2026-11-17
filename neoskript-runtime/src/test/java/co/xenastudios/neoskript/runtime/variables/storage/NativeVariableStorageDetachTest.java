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

/** The same as {@link VariableStorageDetachTest}, for native storage: an addon's values when it's disabled or its codec fails. */
class NativeVariableStorageDetachTest {

    /** Loads {@link AddonCodecs} and its nested classes itself, as an addon's jar's loader would. */
    private static final class AddonLoader extends ClassLoader {
        private static final String PREFIX = AddonCodecs.class.getName();

        AddonLoader() {
            super(NativeVariableStorageDetachTest.class.getClassLoader());
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
    private NativeVariableStorage storage(VariableStore store) throws Exception {
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
        NativeVariableStorage storage = new NativeVariableStorage(store, codecs, dir.resolve("variables.nsv"));
        storage.load();
        return storage;
    }

    /** What the file has for {@code name}: its type and bytes, or {@code null}. */
    private String saved(String name) throws IOException {
        VariableFile.Entry entry = NativeVariableFile.read(Files.readAllBytes(dir.resolve("variables.nsv"))).entries().get(name);
        return entry == null ? null : entry.type() + " " + java.util.HexFormat.of().formatHex(entry.bytes());
    }

    @Test
    void detachKeepsTheAddonsValuesAsSavedBytesAndASaveAfterKeepsThem() throws Exception {
        VariableStore store = new VariableStore();
        NativeVariableStorage storage = storage(store);
        store.set("ticket", ticket(7));
        store.set("wait", Duration.ofSeconds(3));
        store.set("text", "kept");
        assertEquals(new VariableStorage.Detached(List.of("ticket", "wait"), List.of()), storage.detach(addon));
        assertInstanceOf(RawValue.class, store.get("ticket"));
        assertEquals("kept", store.get("text"));
        gone.set(true);
        store.set("stopped", "yes");
        assertEquals(List.of(), storage.save().keptPrevious());
        storage.close();
        assertTrue(saved("ticket").startsWith("test:ticket "));
        assertTrue(saved("wait").startsWith("test:duration "));
        assertTrue(saved("stopped").startsWith("string "));
    }

    @Test
    void anEntryThatCantBeEncodedKeepsItsLastSavedBytes() throws Exception {
        VariableStore store = new VariableStore();
        NativeVariableStorage storage = storage(store);
        store.set("ticket", ticket(7));
        store.set("text", "first");
        storage.save();
        String ticketBefore = saved("ticket");
        gone.set(true);
        store.set("ticket", ticket(8));
        store.set("fresh", ticket(9));
        store.set("text", "second");
        VariableStorage.Result result = storage.save();
        assertEquals(List.of("ticket"), result.keptPrevious());
        assertEquals(List.of("fresh"), result.notSaved());
        storage.close();
        assertEquals(ticketBefore, saved("ticket"), "not deleted, and not changed");
        assertEquals(null, saved("fresh"));
        assertTrue(saved("text").startsWith("string "));
    }

    @Test
    void aCodecThatFailsDuringDetachKeepsTheLastSavedBytes() throws Exception {
        VariableStore store = new VariableStore();
        NativeVariableStorage storage = storage(store);
        store.set("ticket", ticket(7));
        storage.save();
        String ticketBefore = saved("ticket");
        gone.set(true);
        store.set("ticket", ticket(8));
        Object fresh = ticket(9);
        store.set("fresh", fresh);
        VariableStorage.Detached detached = storage.detach(addon);
        assertEquals(List.of("ticket"), detached.kept());
        assertEquals(List.of("fresh"), detached.notSaved());
        assertEquals(fresh, store.get("fresh"));
        storage.save();
        storage.close();
        assertEquals(ticketBefore, saved("ticket"));
    }
}
