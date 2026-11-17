/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;

/**
 * NeoSkript's own variable file ({@code variables.nsv}): an 8-byte header, then records. Each record is one save,
 * applied whole or not at all:
 *
 * <pre>
 *   record  = length (u32) crc32c (u32) payload
 *   payload = kind (u8, 0) types entries
 *   types   = count (varint) then each type name (varint length, UTF-8)
 *   entries = count (varint) then each: shared (varint) suffix (varint length, UTF-8 bytes) type (varint)
 * </pre>
 *
 * <p>A name is the first {@code shared} bytes of the previous name in the record, then its suffix, so names in
 * the same list cost a few bytes. The type is {@code 0} to delete the variable, or {@code 1 + its index} in the
 * record's types followed by the value (varint length, then the bytes of Skript's binary value format, as in
 * {@code variables.csv}). Later records win over earlier ones.
 *
 * <p>Saves append a record. Compaction writes the whole set to a new file, in Skript's order, and swaps it in. A
 * record whose length runs past the end of the file, or whose checksum doesn't match, is where a save was cut
 * off: everything before it is kept, and the reader says where it stopped.
 */
public final class NativeVariableFile {

    /** {@code NSKV}, then the format version (1) and two zero bytes. */
    static final byte[] HEADER = {'N', 'S', 'K', 'V', 0, 1, 0, 0};

    static final int RECORD_HEADER = 8;

    /** Records are split at about this size, so no single one needs a huge buffer. */
    static final int RECORD_TARGET = 1 << 20;

    private static final byte KIND_ENTRIES = 0;

    private NativeVariableFile() {}

    /** A variable as saved: its name, Skript's type name and the value's bytes. {@code type == null} deletes it. */
    public record Op(String name, String type, byte[] bytes) {

        static Op delete(String name) {
            return new Op(name, null, null);
        }
    }

    /** What a file holds, in the order last written, and where the records that could be read end. */
    public record Contents(LinkedHashMap<String, VariableFile.Entry> entries, long validEnd, long length,
            int records) {

        /** Whether bytes after the last good record were ignored: a save that was cut off, or damage. */
        public boolean damaged() {
            return validEnd < length;
        }
    }

    /** Reads a whole file. Throws if it isn't one of NeoSkript's variable files. */
    public static Contents read(byte[] data) throws IOException {
        if (data.length < HEADER.length || !Arrays.equals(data, 0, 4, HEADER, 0, 4)) {
            throw new IOException("not a NeoSkript variable file (it doesn't start with NSKV)");
        }
        if (data[4] != HEADER[4] || data[5] != HEADER[5]) {
            throw new IOException("a NeoSkript variable file of version " + ((data[4] & 0xFF) << 8 | data[5] & 0xFF)
                    + ", newer than this NeoSkript reads (1)");
        }
        LinkedHashMap<String, VariableFile.Entry> entries = new LinkedHashMap<>();
        int position = HEADER.length;
        int records = 0;
        CRC32C crc = new CRC32C();
        while (data.length - position >= RECORD_HEADER) {
            int length = readInt(data, position);
            int checksum = readInt(data, position + 4);
            if (length < 0 || length > data.length - position - RECORD_HEADER) {
                break;
            }
            crc.reset();
            crc.update(data, position + RECORD_HEADER, length);
            if ((int) crc.getValue() != checksum) {
                break;
            }
            if (!apply(data, position + RECORD_HEADER, length, entries)) {
                break;
            }
            position += RECORD_HEADER + length;
            records++;
        }
        return new Contents(entries, position, data.length, records);
    }

    /** Applies one record's entries, or returns false (changing nothing) if it can't be read. */
    private static boolean apply(byte[] data, int offset, int length, Map<String, VariableFile.Entry> into) {
        Reader in = new Reader(data, offset, offset + length);
        try {
            if (in.u8() != KIND_ENTRIES) {
                return false;
            }
            String[] types = new String[in.count()];
            for (int i = 0; i < types.length; i++) {
                types[i] = in.string();
            }
            int count = in.count();
            List<Op> ops = new ArrayList<>(count);
            byte[] previous = new byte[0];
            for (int i = 0; i < count; i++) {
                int shared = in.count();
                int suffix = in.length();
                if (shared > previous.length) {
                    return false;
                }
                byte[] name = Arrays.copyOf(previous, shared + suffix);
                in.bytes(name, shared, suffix);
                previous = name;
                String text = new String(name, StandardCharsets.UTF_8);
                int type = in.count();
                if (type == 0) {
                    ops.add(Op.delete(text));
                } else if (type > types.length) {
                    return false;
                } else {
                    byte[] value = new byte[in.length()];
                    in.bytes(value, 0, value.length);
                    ops.add(new Op(text, types[type - 1], value));
                }
            }
            if (!in.atEnd()) {
                return false;
            }
            // A variable that changes keeps its place, so names stay in the order they were first saved in.
            for (Op op : ops) {
                if (op.type() == null) {
                    into.remove(op.name());
                } else {
                    into.put(op.name(), new VariableFile.Entry(op.name(), op.type(), op.bytes()));
                }
            }
            return true;
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * The ops as one or more records, each with its length and checksum. Ops are split into records of about
     * {@link #RECORD_TARGET} bytes; appending them as one save keeps each record whole.
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
            payload.write(KIND_ENTRIES);
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
