/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The native store when the disk fails: a write or sync that throws mid-save, or a file that can't be reopened. */
class NativeVariableStorageFailureTest {

    @TempDir
    Path dir;

    private final VariableStore store = new VariableStore();

    /** How the next channels opened behave: bytes they may write before failing, and whether syncing fails. */
    private long writeBudget = Long.MAX_VALUE;
    private boolean failForce;
    private boolean failOpen;

    private NativeVariableStorage storage() throws IOException {
        NativeVariableStorage storage = new NativeVariableStorage(store, new VariableCodecs(), dir.resolve("variables.nsv"));
        storage.opener = file -> {
            if (failOpen) {
                throw new IOException("can't open");
            }
            return new FailingChannel(FileChannel.open(file, StandardOpenOption.WRITE));
        };
        storage.load();
        return storage;
    }

    private static RawValue value(String text) {
        return new RawValue("string", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Map<String, Object> reloaded() throws IOException {
        VariableStore loaded = new VariableStore();
        try (NativeVariableStorage storage = new NativeVariableStorage(loaded, new VariableCodecs(), dir.resolve("variables.nsv"))) {
            storage.load();
            assertEquals(0, storage.recoveredBytes(), "nothing cut off");
        }
        return all(loaded);
    }

    private static Map<String, Object> all(VariableStore store) {
        Map<String, Object> values = new LinkedHashMap<>();
        store.forEach(values::put);
        return values;
    }

    @Test
    void aWriteThatFailsMidSaveIsUndoneAndTheNextSaveRewritesTheFile() throws Exception {
        try (NativeVariableStorage storage = storage()) {
            for (int i = 0; i < 50; i++) {
                store.set("v::" + i, value("first " + i));
            }
            storage.save();
            long size = Files.size(dir.resolve("variables.nsv"));
            store.set("v::1", value("second"));
            store.set("v::2", null);
            writeBudget = 5;
            assertThrows(IOException.class, storage::save);
            assertEquals(size, Files.size(dir.resolve("variables.nsv")), "the part written is cut off again");
            writeBudget = Long.MAX_VALUE;
            // The compaction that's due counts as a change, so the next save happens even with nothing changed.
            long modifications = store.modifications();
            storage.save();
            assertFalse(storage.changed());
            store.set("v::3", value("again"));
            assertEquals(modifications + 1, store.modifications());
            storage.save();
            assertEquals(1, storage.compactions(), "a new file instead of appending after a broken save");
        }
        assertEquals(all(store), reloaded());
    }

    @Test
    void aSyncThatFailsIsUndoneToo() throws Exception {
        try (NativeVariableStorage storage = storage()) {
            store.set("a", value("1"));
            storage.save();
            long size = Files.size(dir.resolve("variables.nsv"));
            store.set("a", value("2"));
            failForce = true;
            assertThrows(IOException.class, storage::save);
            assertEquals(size, Files.size(dir.resolve("variables.nsv")));
            failForce = false;
            storage.save();
        }
        assertEquals(value("2"), reloaded().get("a"));
    }

    @Test
    void aFileThatCantBeReopenedAfterCompactingIsRewrittenByTheNextSave() throws Exception {
        try (NativeVariableStorage storage = storage()) {
            store.set("a", value("1"));
            storage.save();
            // A failed append makes the next save compact, and then the file can't be opened again.
            store.set("a", value("2"));
            writeBudget = 0;
            assertThrows(IOException.class, storage::save);
            writeBudget = Long.MAX_VALUE;
            failOpen = true;
            store.set("b", value("3"));
            storage.save();
            assertEquals(value("3"), reloaded().get("b"), "the compacted file is whole");
            // No channel to append to: the next save compacts again instead of failing.
            failOpen = false;
            store.set("c", value("4"));
            storage.save();
            assertEquals(2, storage.compactions());
        }
        assertEquals(all(store), reloaded());
    }

    @Test
    void nothingSavesAfterClosing() throws Exception {
        NativeVariableStorage storage = storage();
        storage.close();
        assertFalse(storage.loaded());
        assertFalse(storage.changed());
        assertThrows(IllegalStateException.class, storage::save);
    }

    /** A file channel that fails as the test says. */
    private final class FailingChannel extends FileChannel {
        private final FileChannel file;

        FailingChannel(FileChannel file) {
            this.file = file;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (writeBudget <= 0) {
                throw new IOException("disk full");
            }
            int allowed = (int) Math.min(src.remaining(), writeBudget);
            ByteBuffer part = src.slice(src.position(), allowed);
            int written = file.write(part);
            src.position(src.position() + written);
            writeBudget -= written;
            return written;
        }

        @Override
        public void force(boolean metaData) throws IOException {
            if (failForce) {
                throw new IOException("sync failed");
            }
            file.force(metaData);
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return file.read(dst);
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return file.read(dsts, offset, length);
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() throws IOException {
            return file.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            file.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return file.size();
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            file.truncate(size);
            return this;
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            return file.transferTo(position, count, target);
        }

        @Override
        public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
            return file.transferFrom(src, position, count);
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            return file.read(dst, position);
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            if (writeBudget <= 0) {
                throw new IOException("disk full");
            }
            int written = file.write(src, position);
            writeBudget -= written;
            return written;
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return file.map(mode, position, size);
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            return file.lock(position, size, shared);
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return file.tryLock(position, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {
            file.close();
        }
    }
}
