/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The native file's blocks and commits, read directly. */
class NativeVariableFileTest {

    @TempDir
    Path dir;

    private static byte[] file(byte[] id, long sequence0, long sequence1) {
        List<byte[]> records = NativeVariableFile.records(List.of(new NativeVariableFile.Op("a", "string", new byte[] {1})));
        int end = NativeVariableFile.HEADER_LENGTH + records.getFirst().length;
        byte[] data = new byte[end];
        System.arraycopy(NativeVariableFile.header(id, 1, end), 0, data, 0, NativeVariableFile.HEADER_LENGTH);
        System.arraycopy(records.getFirst(), 0, data, NativeVariableFile.HEADER_LENGTH, records.getFirst().length);
        System.arraycopy(NativeVariableFile.commit(id, sequence0, end), 0, data, (int) NativeVariableFile.commitOffset(0),
                NativeVariableFile.COMMIT_LENGTH);
        if (sequence1 != 0) {
            System.arraycopy(NativeVariableFile.commit(id, sequence1, NativeVariableFile.HEADER_LENGTH), 0, data,
                    (int) NativeVariableFile.commitOffset(1), NativeVariableFile.COMMIT_LENGTH);
        }
        return data;
    }

    @Test
    void eachBlockIsAPageOfItsOwn() {
        assertEquals(4096, NativeVariableFile.commitOffset(0));
        assertEquals(8192, NativeVariableFile.commitOffset(1));
        assertEquals(12288, NativeVariableFile.HEADER_LENGTH);
    }

    @Test
    void sequencesCompareUnsigned() throws Exception {
        byte[] id = NativeVariableFile.newId();
        // Slot 0 has the highest sequence there can be, which is negative as a signed long; slot 1 has 1.
        NativeVariableFile.Contents contents = NativeVariableFile.read(file(id, -1L, 1L));
        assertEquals(0, contents.slot());
        assertEquals(1, contents.entries().size());
    }

    @Test
    void aGarbledIdentityBlockAloneStillLoads() throws Exception {
        byte[] data = file(NativeVariableFile.newId(), 1, 0);
        for (int i = 0; i < 4096; i++) {
            data[i] = (byte) 0xA5;
        }
        assertEquals(1, NativeVariableFile.read(data).entries().size());
    }

    @Test
    void aWholeCommitOfANewerFormatIsRefused() {
        byte[] id = NativeVariableFile.newId();
        byte[] data = file(id, 1, 0);
        // Flags set in an otherwise whole commit: a newer NeoSkript wrote it.
        byte[] commit = NativeVariableFile.commit(id, 2, data.length);
        commit[7] = 1;
        java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
        crc.update(commit, 0, NativeVariableFile.COMMIT_LENGTH - 4);
        ByteBuffer.wrap(commit).putInt(NativeVariableFile.COMMIT_LENGTH - 4, (int) crc.getValue());
        System.arraycopy(commit, 0, data, (int) NativeVariableFile.commitOffset(1), commit.length);
        IOException refused = assertThrows(IOException.class, () -> NativeVariableFile.read(data));
        assertFalse(refused instanceof NativeVariableFile.Damaged, "not damage: recovery mustn't drop it");
    }

    @Test
    void aCommitOfAnotherFileIsntUsed() {
        byte[] data = file(NativeVariableFile.newId(), 1, 0);
        System.arraycopy(NativeVariableFile.commit(NativeVariableFile.newId(), 9, data.length), 0, data,
                (int) NativeVariableFile.commitOffset(0), NativeVariableFile.COMMIT_LENGTH);
        assertInstanceOf(NativeVariableFile.Damaged.class, assertThrows(IOException.class, () -> NativeVariableFile.read(data)));
    }

    @Test
    void aFailedLoadLeavesNoChannelOpen() throws Exception {
        VariableStore store = new VariableStore();
        Path file = dir.resolve("variables.nsv");
        try (NativeVariableStorage storage = new NativeVariableStorage(store, new VariableCodecs(), file)) {
            storage.load();
            store.set("a", new RawValue("string", new byte[] {1}));
            storage.save();
        }
        List<FileChannel> opened = new java.util.ArrayList<>();
        NativeVariableStorage storage = new NativeVariableStorage(new VariableStore(), new VariableCodecs(), file);
        storage.opener = path -> {
            FileChannel channel = new PositionFails(FileChannel.open(path, StandardOpenOption.WRITE));
            opened.add(channel);
            return channel;
        };
        assertThrows(IOException.class, storage::load);
        assertFalse(storage.loaded());
        assertEquals(1, opened.size());
        assertFalse(opened.getFirst().isOpen(), "closed again");
    }

    @Test
    void snapshotsArePrunedByGenerationNotByTime() throws Exception {
        Path file = dir.resolve("variables.nsv");
        for (int i = 0; i < 5; i++) {
            NativeVariableStorage.write(file, List.of(new NativeVariableFile.Op("v", "string", new byte[] {(byte) i})));
            // Each snapshot's time goes backwards, as after a clock step.
            for (Path snapshot : Snapshots.list(file)) {
                if (NativeVariableFile.generation(snapshot) == i + 1) {
                    Files.setLastModifiedTime(snapshot, java.nio.file.attribute.FileTime.fromMillis(1_000_000_000L - i * 1000L));
                }
            }
        }
        List<Long> kept = Snapshots.list(file).stream().map(NativeVariableFile::generation).toList();
        assertEquals(List.of(5L, 4L, 3L), kept);
        assertEquals(5, NativeVariableFile.generation(file));
    }

    /** A channel that opens, then fails to be positioned. */
    private static final class PositionFails extends FileChannel {
        private final FileChannel file;

        PositionFails(FileChannel file) {
            this.file = file;
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            throw new IOException("can't seek");
        }

        @Override
        public long position() throws IOException {
            return file.position();
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
        public int write(ByteBuffer src) throws IOException {
            return file.write(src);
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            return file.write(srcs, offset, length);
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
        public void force(boolean metaData) throws IOException {
            file.force(metaData);
        }

        @Override
        public long transferTo(long position, long count, java.nio.channels.WritableByteChannel target)
                throws IOException {
            return file.transferTo(position, count, target);
        }

        @Override
        public long transferFrom(java.nio.channels.ReadableByteChannel src, long position, long count)
                throws IOException {
            return file.transferFrom(src, position, count);
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            return file.read(dst, position);
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            return file.write(src, position);
        }

        @Override
        public java.nio.MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return file.map(mode, position, size);
        }

        @Override
        public java.nio.channels.FileLock lock(long position, long size, boolean shared) throws IOException {
            return file.lock(position, size, shared);
        }

        @Override
        public java.nio.channels.FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return file.tryLock(position, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {
            file.close();
        }
    }
}
