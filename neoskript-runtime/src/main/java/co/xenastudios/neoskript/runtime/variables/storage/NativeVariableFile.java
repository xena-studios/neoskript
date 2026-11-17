/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;

/**
 * NeoSkript's own variable file ({@code variables.nsv}): three 4 KiB blocks, then saves. Each save is one record,
 * or several when it's large, applied whole or not at all:
 *
 * <pre>
 *   block 0 = identity: "NSKV" version (u16, 1) flags (u16, 0) file id (16 bytes) crc32c of the 24 before (u32)
 *   block 1 = commit slot 0, block 2 = commit slot 1
 *   commit  = "NSKC" version (u16) flags (u16) file id (16) sequence (u64) end (u64) crc32c of the 40 before (u32)
 *   record  = length (u32, at least 1) crc32c (u32) payload
 *   payload = kind (u8) types entries
 *   kind    = 0 for a save's last (or only) record, 1 for a record the next one continues
 *   types   = count (varint) then each type name (varint length, UTF-8)
 *   entries = count (varint) then each: shared (varint) suffix (varint length, UTF-8 bytes) type (varint)
 * </pre>
 *
 * <p>A name is the first {@code shared} bytes of the previous name in the record, then its suffix, so names in
 * the same list cost a few bytes. The type is {@code 0} to delete the variable, or {@code 1 + its index} in the
 * record's types followed by the value (varint length, then the bytes of Skript's binary value format, as in
 * {@code variables.csv}). Later saves win over earlier ones.
 *
 * <p><b>Blocks.</b> Each block is a page of its own, so a power cut while one is written can only garble that one.
 * The identity is written once, when the file is made, and never again. Each commit slot carries its own magic,
 * version and file id, so the file loads from either slot even if the identity block is garbled. The file id is
 * random for each file written, and snapshots of the file in {@code backups/} carry it too, so a snapshot is only
 * ever matched to its own file.
 *
 * <p><b>Commits.</b> Compaction writes a new file, whose commit in slot 0 ends at the end of the file, syncs it and
 * renames it over the old one. A save appends its records and syncs them, then writes the other commit slot with the
 * next sequence number and the new end, and syncs that. The valid commit with the highest sequence (compared
 * unsigned) says where the committed saves end.
 *
 * <p><b>Crash or damage.</b> Everything before the committed end was synced before the commit was written, so it
 * must read perfectly: anything wrong there is damage ({@link Damaged}). After the committed end is at most one save
 * whose commit didn't happen. If it's whole, it's applied. Anything else after the committed end, in any state (cut
 * short, torn in any order, zero-filled by the file system), is the tail of a save a crash cut off, and is dropped.
 * No bytes after it are looked at, so values can't look like records.
 */
public final class NativeVariableFile {

    static final int BLOCK = 4096;

    private static final byte[] IDENTITY_MAGIC = {'N', 'S', 'K', 'V'};
    private static final byte[] COMMIT_MAGIC = {'N', 'S', 'K', 'C'};
    private static final short VERSION = 1;
    static final int ID_LENGTH = 16;
    private static final int IDENTITY_LENGTH = 28;
    static final int COMMIT_LENGTH = 44;

    /** Where the first record starts, after the identity and the two commit slots. */
    public static final int HEADER_LENGTH = 3 * BLOCK;

    /** The largest file read: what fits in one array. */
    static final long MAX_LENGTH = Integer.MAX_VALUE - 16;

    static final int RECORD_HEADER = 8;

    /** Records are split at about this size, so no single one needs a huge buffer. */
    static final int RECORD_TARGET = 1 << 20;

    /** A save's last or only record. */
    private static final byte KIND_ENTRIES = 0;

    /** A record of a save that goes on in the next record. */
    private static final byte KIND_CONTINUES = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private NativeVariableFile() {}

    /** A variable as saved: its name, Skript's type name and the value's bytes. {@code type == null} deletes it. */
    public record Op(String name, String type, byte[] bytes) {

        static Op delete(String name) {
            return new Op(name, null, null);
        }
    }

    /**
     * What a file holds, in the order last written. {@code committed} is where the committed saves end, by the
     * commit with sequence {@code sequence} in slot {@code slot}; {@code validEnd} is where the last whole save ends
     * (after {@code committed} when a save's commit didn't happen); {@code length} is the file's length.
     */
    public record Contents(LinkedHashMap<String, VariableFile.Entry> entries, byte[] fileId, long committed,
            long sequence, int slot, long validEnd, long length, int records) {

        /** Whether bytes after the last whole save are to be dropped: a save a crash cut off. */
        public boolean damaged() {
            return validEnd < length;
        }
    }

    /**
     * Damage before the committed end, or to both commit slots: a fault a crash can't explain. {@code position} is
     * where the save that can't be read starts (or the first commit slot, if neither can be read); {@code fileId}
     * is the file's id if it could be read, else {@code null}; {@code committed} is where the committed saves end,
     * or -1 if no commit could be read.
     */
    public static final class Damaged extends IOException {
        private final long position;
        private final transient byte[] fileId;
        private final long committed;

        Damaged(String message, long position, byte[] fileId, long committed) {
            super(message);
            this.position = position;
            this.fileId = fileId;
            this.committed = committed;
        }

        public long position() {
            return position;
        }

        public byte[] fileId() {
            return fileId;
        }

        public long committed() {
            return committed;
        }
    }

    /** A new random file id. */
    static byte[] newId() {
        byte[] id = new byte[ID_LENGTH];
        RANDOM.nextBytes(id);
        return id;
    }

    /** A commit slot's bytes. */
    static byte[] commit(byte[] fileId, long sequence, long end) {
        ByteBuffer buffer = ByteBuffer.allocate(COMMIT_LENGTH);
        buffer.put(COMMIT_MAGIC).putShort(VERSION).putShort((short) 0).put(fileId).putLong(sequence).putLong(end);
        CRC32C crc = new CRC32C();
        crc.update(buffer.array(), 0, COMMIT_LENGTH - 4);
        buffer.putInt((int) crc.getValue());
        return buffer.array();
    }

    /** Where commit slot {@code slot} (0 or 1) is in the file. */
    static long commitOffset(int slot) {
        return (long) BLOCK * (1 + slot);
    }

    /** A new file's three blocks: its identity, and a commit in slot 0 up to {@code end}. */
    static byte[] header(byte[] fileId, long end) {
        byte[] header = new byte[HEADER_LENGTH];
        ByteBuffer identity = ByteBuffer.wrap(header, 0, IDENTITY_LENGTH);
        identity.put(IDENTITY_MAGIC).putShort(VERSION).putShort((short) 0).put(fileId);
        CRC32C crc = new CRC32C();
        crc.update(header, 0, IDENTITY_LENGTH - 4);
        identity.putInt((int) crc.getValue());
        System.arraycopy(commit(fileId, 1, end), 0, header, (int) commitOffset(0), COMMIT_LENGTH);
        // Slot 1 is left zero, which isn't a commit.
        return header;
    }

    /** Reads {@code file} whole, or throws a clear error if it's too large to. */
    public static byte[] readFile(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_LENGTH) {
            throw new IOException(file.getFileName() + " is " + size / (1 << 20) + " MiB, larger than the 2 GiB "
                    + "NeoSkript reads. Switch to csv storage with a copy of it on a NeoSkript that can, or ask for help.");
        }
        try {
            return Files.readAllBytes(file);
        } catch (OutOfMemoryError e) {
            throw new IOException(file.getFileName() + " (" + size / (1 << 20) + " MiB) doesn't fit in the server's "
                    + "memory. Give the server more memory (-Xmx) and start it again.", e);
        }
    }

    /** A block that's whole: its version, flags and file id, and for a commit, its sequence and end. */
    private record Block(int version, int flags, byte[] fileId, long sequence, long end) {}

    private static Block block(byte[] data, int at, byte[] magic, int length) {
        if (data.length < at + length || !Arrays.equals(data, at, at + 4, magic, 0, 4)) {
            return null;
        }
        CRC32C crc = new CRC32C();
        crc.update(data, at, length - 4);
        if ((int) crc.getValue() != readInt(data, at + length - 4)) {
            return null;
        }
        ByteBuffer in = ByteBuffer.wrap(data, at + 4, length - 8);
        int version = in.getShort() & 0xFFFF;
        int flags = in.getShort() & 0xFFFF;
        byte[] id = new byte[ID_LENGTH];
        in.get(id);
        long sequence = length == COMMIT_LENGTH ? in.getLong() : 0;
        long end = length == COMMIT_LENGTH ? in.getLong() : 0;
        return new Block(version, flags, id, sequence, end);
    }

    private static void supported(Block block) throws IOException {
        if (block.version() != VERSION || block.flags() != 0) {
            throw new IOException("a NeoSkript variable file of version " + block.version()
                    + (block.flags() != 0 ? " with features" : "") + " newer than this NeoSkript reads");
        }
    }

    /**
     * Reads a whole file. Throws {@link Damaged} if anything before the committed end can't be read or neither
     * commit can, and a plain {@link IOException} if it isn't one of NeoSkript's variable files or is of a newer
     * version.
     */
    public static Contents read(byte[] data) throws IOException {
        Block identity = block(data, 0, IDENTITY_MAGIC, IDENTITY_LENGTH);
        Block[] commits = {block(data, (int) commitOffset(0), COMMIT_MAGIC, COMMIT_LENGTH),
                block(data, (int) commitOffset(1), COMMIT_MAGIC, COMMIT_LENGTH)};
        if (identity != null) {
            supported(identity);
        }
        for (Block commit : commits) {
            if (commit != null) {
                supported(commit);
            }
        }
        byte[] fileId = identity != null ? identity.fileId() : null;
        int slot = -1;
        for (int i = 0; i < 2; i++) {
            Block commit = commits[i];
            if (commit == null || fileId != null && !Arrays.equals(fileId, commit.fileId())) {
                continue;
            }
            if (slot < 0 || Long.compareUnsigned(commit.sequence(), commits[slot].sequence()) > 0) {
                slot = i;
            }
        }
        if (slot < 0) {
            boolean ours = identity != null || data.length >= 4 && Arrays.equals(data, 0, 4, IDENTITY_MAGIC, 0, 4);
            if (!ours) {
                throw new IOException("not a NeoSkript variable file (it doesn't start with NSKV)");
            }
            throw new Damaged("damaged: neither copy of its commit can be read", commitOffset(0), fileId, -1);
        }
        Block commit = commits[slot];
        fileId = commit.fileId();
        long committed = commit.end();
        if (committed < HEADER_LENGTH || committed > data.length) {
            throw new Damaged("damaged: its commit says the saves end at byte " + committed + ", but the file is "
                    + data.length + " bytes", HEADER_LENGTH, fileId, committed);
        }
        LinkedHashMap<String, VariableFile.Entry> entries = new LinkedHashMap<>();
        // The committed saves: synced before their commit, so anything wrong is damage.
        int[] saves = {0};
        int end = saves(data, HEADER_LENGTH, (int) committed, entries, saves);
        if (end != committed) {
            throw new Damaged("damaged: the save at byte " + end + " can't be read, before the end of the saves"
                    + " committed at byte " + committed, end, fileId, committed);
        }
        // After them, a save whose commit didn't happen: applied if it's whole, else the tail of a crash.
        int validEnd = saves(data, end, data.length, entries, saves);
        return new Contents(entries, fileId, committed, commit.sequence(), slot, validEnd, data.length, saves[0]);
    }

    /**
     * The whole saves from the first record on, up to the first that can't be read, ignoring commits: what's
     * left of a damaged file. {@code stopped[0]} is set to where reading stopped.
     */
    static LinkedHashMap<String, VariableFile.Entry> salvage(byte[] data, int[] saves, long[] stopped)
            throws IOException {
        LinkedHashMap<String, VariableFile.Entry> entries = new LinkedHashMap<>();
        stopped[0] = data.length < HEADER_LENGTH ? HEADER_LENGTH : saves(data, HEADER_LENGTH, data.length, entries, saves);
        return entries;
    }

    /**
     * The saves after a damaged one that can still be read, skipping the damaged record by its length if that's
     * believable: only to say what a recovery loses, never applied. Their variables' names, in order.
     */
    static List<String> readableAfter(byte[] data, long damaged, long end, int[] saves) {
        List<String> names = new ArrayList<>();
        int at = (int) damaged;
        if (at + RECORD_HEADER > end) {
            return names;
        }
        int length = readInt(data, at);
        if (length < 1 || at + RECORD_HEADER + (long) length > end) {
            return names;
        }
        Map<String, VariableFile.Entry> scratch = new LinkedHashMap<>() {
            @Override
            public VariableFile.Entry put(String key, VariableFile.Entry value) {
                names.add(key);
                return null;
            }

            @Override
            public VariableFile.Entry remove(Object key) {
                names.add((String) key);
                return null;
            }
        };
        try {
            saves(data, at + RECORD_HEADER + length, (int) end, scratch, saves);
        } catch (IOException e) {
            // Only a count for the log.
        }
        return names;
    }

    /**
     * Applies the whole saves in {@code data[from, to)}, stopping at the first one that can't be read; where it
     * stopped. Throws only for a record whose checksum is right but that can't be read: a newer NeoSkript wrote it.
     */
    private static int saves(byte[] data, int from, int to, Map<String, VariableFile.Entry> into, int[] count)
            throws IOException {
        List<Op> save = new ArrayList<>();
        int position = from;
        int saveStart = from;
        CRC32C crc = new CRC32C();
        while (to - position >= RECORD_HEADER) {
            int length = readInt(data, position);
            int checksum = readInt(data, position + 4);
            // A record has at least its kind, so a length of 0 (a zero-filled tail) is never one.
            if (length < 1 || length > to - position - RECORD_HEADER) {
                break;
            }
            crc.reset();
            crc.update(data, position + RECORD_HEADER, length);
            if ((int) crc.getValue() != checksum) {
                break;
            }
            Boolean continues = parse(data, position + RECORD_HEADER, length, save);
            if (continues == null) {
                throw new IOException("the record at byte " + position + " is whole but can't be read, so it was"
                        + " written by a newer NeoSkript");
            }
            position += RECORD_HEADER + length;
            if (!continues) {
                apply(save, into);
                save.clear();
                saveStart = position;
                count[0]++;
            }
        }
        return saveStart;
    }

    private static void apply(List<Op> ops, Map<String, VariableFile.Entry> into) {
        // A variable that changes keeps its place, so names stay in the order they were first saved in.
        for (Op op : ops) {
            if (op.type() == null) {
                into.remove(op.name());
            } else {
                into.put(op.name(), new VariableFile.Entry(op.name(), op.type(), op.bytes()));
            }
        }
    }

    /**
     * Adds one record's entries to {@code into}, and returns whether the save goes on in the next record, or
     * {@code null} (adding nothing) if the record can't be read.
     */
    private static Boolean parse(byte[] data, int offset, int length, List<Op> into) {
        Reader in = new Reader(data, offset, offset + length);
        try {
            int kind = in.u8();
            if (kind != KIND_ENTRIES && kind != KIND_CONTINUES) {
                return null;
            }
            String[] types = new String[in.count()];
            for (int i = 0; i < types.length; i++) {
                types[i] = in.string();
            }
            int count = in.count();
            List<Op> ops = new ArrayList<>(Math.min(count, length));
            byte[] previous = new byte[0];
            for (int i = 0; i < count; i++) {
                int shared = in.count();
                int suffix = in.length();
                if (shared > previous.length) {
                    return null;
                }
                byte[] name = Arrays.copyOf(previous, shared + suffix);
                in.bytes(name, shared, suffix);
                previous = name;
                String text = new String(name, StandardCharsets.UTF_8);
                int type = in.count();
                if (type == 0) {
                    ops.add(Op.delete(text));
                } else if (type > types.length) {
                    return null;
                } else {
                    byte[] value = new byte[in.length()];
                    in.bytes(value, 0, value.length);
                    ops.add(new Op(text, types[type - 1], value));
                }
            }
            if (!in.atEnd()) {
                return null;
            }
            into.addAll(ops);
            return kind == KIND_CONTINUES;
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The ops as one save of one or more records, each with its length and checksum. Ops are split into records of
     * about {@link #RECORD_TARGET} bytes, and all but the last are marked as going on in the next.
     */
    public static List<byte[]> records(List<Op> ops) {
        List<byte[]> records = new ArrayList<>();
        int from = 0;
        while (from < ops.size() || records.isEmpty()) {
            Map<String, Integer> typeIndex = new HashMap<>();
            List<String> types = new ArrayList<>();
            Writer body = new Writer();
            byte[] previous = new byte[0];
            int count = 0;
            int to = from;
            while (to < ops.size() && (count == 0 || body.size() < RECORD_TARGET)) {
                Op op = ops.get(to++);
                byte[] name = op.name().getBytes(StandardCharsets.UTF_8);
                int shared = Arrays.mismatch(previous, name);
                if (shared < 0) {
                    shared = name.length;
                }
                shared = Math.min(shared, Math.min(previous.length, name.length));
                body.varint(shared);
                body.varint(name.length - shared);
                body.write(name, shared, name.length - shared);
                previous = name;
                if (op.type() == null) {
                    body.varint(0);
                } else {
                    Integer index = typeIndex.get(op.type());
                    if (index == null) {
                        index = types.size();
                        typeIndex.put(op.type(), index);
                        types.add(op.type());
                    }
                    body.varint(index + 1);
                    body.varint(op.bytes().length);
                    body.write(op.bytes(), 0, op.bytes().length);
                }
                count++;
            }
            Writer payload = new Writer();
            // Every record of the save but its last says the save goes on.
            payload.write(to < ops.size() ? KIND_CONTINUES : KIND_ENTRIES);
            payload.varint(types.size());
            for (String type : types) {
                byte[] bytes = type.getBytes(StandardCharsets.UTF_8);
                payload.varint(bytes.length);
                payload.write(bytes, 0, bytes.length);
            }
            payload.varint(count);
            body.writeTo(payload);
            byte[] content = payload.toByteArray();
            CRC32C crc = new CRC32C();
            crc.update(content);
            byte[] record = new byte[RECORD_HEADER + content.length];
            writeInt(record, 0, content.length);
            writeInt(record, 4, (int) crc.getValue());
            System.arraycopy(content, 0, record, RECORD_HEADER, content.length);
            records.add(record);
            from = to;
        }
        return records;
    }

    private static int readInt(byte[] data, int at) {
        return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | data[at + 3] & 0xFF;
    }

    private static void writeInt(byte[] data, int at, int value) {
        data[at] = (byte) (value >>> 24);
        data[at + 1] = (byte) (value >>> 16);
        data[at + 2] = (byte) (value >>> 8);
        data[at + 3] = (byte) value;
    }

    private static final class Writer extends ByteArrayOutputStream {

        void varint(int value) {
            while ((value & ~0x7F) != 0) {
                write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            write(value);
        }

        void writeTo(Writer other) {
            other.write(buf, 0, count);
        }
    }

    private static final class Reader {
        private final byte[] data;
        private final int end;
        private int position;

        Reader(byte[] data, int position, int end) {
            this.data = data;
            this.position = position;
            this.end = end;
        }

        int u8() {
            if (position >= end) {
                throw new IndexOutOfBoundsException();
            }
            return data[position++] & 0xFF;
        }

        /** A varint that's a count or length: never negative, at most 5 bytes. */
        int count() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int b = u8();
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    if (value < 0) {
                        throw new IllegalArgumentException("negative length");
                    }
                    return value;
                }
            }
            throw new IllegalArgumentException("varint too long");
        }

        /** A length of bytes that follow, which has to fit in what's left. */
        int length() {
            int length = count();
            if (length > end - position) {
                throw new IndexOutOfBoundsException();
            }
            return length;
        }

        String string() {
            int length = length();
            String text = new String(data, position, length, StandardCharsets.UTF_8);
            position += length;
            return text;
        }

        void bytes(byte[] into, int offset, int length) {
            if (length > end - position) {
                throw new IndexOutOfBoundsException();
            }
            System.arraycopy(data, position, into, offset, length);
            position += length;
        }

        boolean atEnd() {
            return position == end;
        }
    }
}
