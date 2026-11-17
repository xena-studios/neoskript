/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Saves global variables to NeoSkript's own file ({@link NativeVariableFile}), an append-only log of what changed,
 * compacted now and then. Values are in Skript's binary format, as in {@code variables.csv}, so converting either
 * way loses nothing.
 *
 * <ul>
 *   <li>A save appends only the variables that changed or were deleted since the last one, syncs them, and then
 *       commits them: it writes their end into the header and syncs that ({@link NativeVariableFile}). A save
 *       a crash cuts off before its commit is dropped when the file is next read, so a save either happened or
 *       didn't, and damage to committed saves is refused rather than dropped.
 *   <li>When the file is more than twice what its variables need (and at least 1 MiB more), a save writes the
 *       whole set to a new file and swaps it in instead.
 * </ul>
 *
 * <p>To tell what changed, the bytes last saved for each variable are kept. A variable that holds the same
 * text, number, boolean or timespan object as last time isn't encoded again; anything else is, and compared.
 */
public final class NativeVariableStorage implements VariableBackend {

    /** How far past twice the variables' size the file may grow before it's compacted. */
    static final long COMPACT_SLACK = 1 << 20;

    /** What was last saved for a variable. */
    private static final class Saved {
        Object value;
        final String type;
        final byte[] bytes;
        long seen;

        Saved(Object value, String type, byte[] bytes) {
            this.value = value;
            this.type = type;
            this.bytes = bytes;
        }

        static long size(String name, String type, byte[] bytes) {
            return name.length() + type.length() + bytes.length + 6;
        }
    }

    private final VariableStore store;
    private final VariableCodecs codecs;
    private final Path file;
    private final Map<String, Saved> saved = new HashMap<>();
    private volatile boolean loaded;
    private volatile long savedAt = -1;
    private FileChannel channel;
    private long fileBytes;
    private long liveBytes;
    private long round;
    private volatile boolean compactNext;
    private long sequence;
    private int slot;
    private Path recoveryBackup;
    private long recoveredBytes;
    private int appends;
    private int compactions;

    public NativeVariableStorage(VariableStore store, VariableCodecs codecs, Path file) {
        this.store = store;
        this.codecs = codecs;
        this.file = file;
    }

    @Override
    public Path file() {
        return file;
    }

    @Override
    public boolean loaded() {
        return loaded;
    }

    /**
     * If the last load found the file cut off or damaged after its last good record, where the whole file was
     * backed up before the rest was dropped, else {@code null}.
     */
    public Path recoveryBackup() {
        return recoveryBackup;
    }

    /** How many bytes at the end of the file the last load dropped (see {@link #recoveryBackup}). */
    public long recoveredBytes() {
        return recoveredBytes;
    }

    /** Saves that appended a record, and saves that compacted the file, since this was made. For tests. */
    int appends() {
        return appends;
    }

    int compactions() {
        return compactions;
    }

    @Override
    public synchronized VariableStorage.Result load() throws IOException {
        loaded = false;
        closeChannel();
        saved.clear();
        liveBytes = 0;
        recoveryBackup = null;
        recoveredBytes = 0;
        removeLeftovers();
        if (!Files.exists(file)) {
            write(file, List.of());
            fileBytes = Files.size(file);
            sequence = 1;
            slot = 0;
            openChannel();
            savedAt = store.modifications();
            loaded = true;
            return new VariableStorage.Result(0, 0, 0);
        }
        NativeVariableFile.Contents contents = NativeVariableFile.read(Files.readAllBytes(file));
        if (contents.damaged()) {
            // Keep the file as it was, synced, before the save a crash cut off after the last commit is dropped.
            recoveryBackup = VariableStorage.backup(file);
            recoveredBytes = contents.length() - contents.validEnd();
            try (FileChannel truncate = FileChannel.open(file, StandardOpenOption.WRITE)) {
                truncate.truncate(contents.validEnd());
                truncate.force(true);
            }
        }
        int raw = 0;
        List<String> names = new ArrayList<>(contents.entries().size());
        List<Object> values = new ArrayList<>(contents.entries().size());
        List<VariableFile.Problem> skipped = new ArrayList<>();
        for (VariableFile.Entry entry : contents.entries().values()) {
            Object value;
            try {
                value = codecs.decode(entry.type(), entry.bytes());
            } catch (RuntimeException e) {
                skipped.add(new VariableFile.Problem(0, entry.name(), entry.name(),
                        "variable " + entry.name() + " could not be loaded: " + e));
                continue;
            }
            if (value instanceof RawValue) {
                raw++;
            }
            names.add(entry.name());
            values.add(value);
            saved.put(entry.name(), new Saved(value, entry.type(), entry.bytes()));
            liveBytes += Saved.size(entry.name(), entry.type(), entry.bytes());
        }
        // As for variables.csv: values that can't be read are skipped, after the file is backed up.
        Path backup = recoveryBackup;
        if (!skipped.isEmpty() && backup == null) {
            backup = VariableStorage.backup(file);
        }
        // The backup keeps the values skipped, and the first save writes the file without them, so they aren't
        // skipped and backed up again on every start.
        // A compacted file is in Skript's order, which the store takes in one pass.
        store.load(names, values);
        fileBytes = contents.validEnd();
        sequence = contents.sequence();
        slot = contents.slot();
        // A file of many saves that's grown past its limit is compacted by the first save.
        compactNext = !skipped.isEmpty() || contents.records() > 1 && fileBytes > 2 * liveBytes + COMPACT_SLACK;
        openChannel();
        if (contents.validEnd() != contents.committed()) {
            // A whole save whose commit didn't happen, or a tail just dropped: commit where the saves end now.
            commit(contents.validEnd());
        }
        savedAt = store.modifications();
        loaded = true;
        return new VariableStorage.Result(values.size(), raw, 0, List.copyOf(skipped), backup, 0);
    }

    @Override
    public boolean changed() {
        // A compaction that's due counts, so it happens at the next save even with nothing changed.
        return loaded && (store.modifications() != savedAt || compactNext);
    }

    @Override
    public synchronized VariableStorage.Result save() throws IOException {
        if (!loaded) {
            throw new IllegalStateException("The variables in " + file + " weren't loaded, so they aren't saved");
        }
        long modifications = store.modifications();
        long current = ++round;
        List<NativeVariableFile.Op> all = new ArrayList<>(saved.size());
        List<NativeVariableFile.Op> changes = new ArrayList<>();
        Map<String, Saved> updated = new HashMap<>();
        int[] counts = new int[2];
        store.forEach((variable, value) -> {
            if (variable.startsWith("-")) {
                return;
            }
            String name = variable.toLowerCase(Locale.ROOT);
            Saved last = saved.get(name);
            if (last != null && last.value == value && immutable(value)) {
                last.seen = current;
                all.add(new NativeVariableFile.Op(name, last.type, last.bytes));
                if (value instanceof RawValue) {
                    counts[0]++;
                }
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
            NativeVariableFile.Op op = new NativeVariableFile.Op(name, encoded.type(), encoded.bytes());
            all.add(op);
            if (last != null && last.type.equals(encoded.type()) && Arrays.equals(last.bytes, encoded.bytes())) {
                last.seen = current;
                last.value = value;
                return;
            }
            Saved next = new Saved(value, encoded.type(), encoded.bytes());
            next.seen = current;
            updated.put(name, next);
            changes.add(op);
        });
        List<String> deleted = new ArrayList<>();
        for (Map.Entry<String, Saved> entry : saved.entrySet()) {
            if (entry.getValue().seen != current && !updated.containsKey(entry.getKey())) {
                deleted.add(entry.getKey());
                changes.add(NativeVariableFile.Op.delete(entry.getKey()));
            }
        }
        long live = liveBytes;
        for (Map.Entry<String, Saved> entry : updated.entrySet()) {
            Saved last = saved.get(entry.getKey());
            if (last != null) {
                live -= Saved.size(entry.getKey(), last.type, last.bytes);
            }
            live += Saved.size(entry.getKey(), entry.getValue().type, entry.getValue().bytes);
        }
        for (String name : deleted) {
            Saved last = saved.get(name);
            live -= Saved.size(name, last.type, last.bytes);
        }
        if (!changes.isEmpty() || compactNext) {
            List<byte[]> records = NativeVariableFile.records(changes);
            long appended = 0;
            for (byte[] record : records) {
                appended += record.length;
            }
            if (compactNext || channel == null || fileBytes + appended > 2 * live + COMPACT_SLACK) {
                compact(all);
            } else {
                append(records, appended);
            }
            // Only now that it's on disk does it count as saved.
            saved.putAll(updated);
            for (String name : deleted) {
                saved.remove(name);
            }
            liveBytes = live;
        }
        savedAt = modifications;
        return new VariableStorage.Result(all.size(), counts[0], counts[1]);
    }

    /** Values that can't change in place, so the same object means the same saved bytes. */
    private static boolean immutable(Object value) {
        return value instanceof String || value instanceof Long || value instanceof Double || value instanceof Boolean
                || value instanceof Integer || value instanceof Timespan || value instanceof RawValue;
    }

    private void append(List<byte[]> records, long appended) throws IOException {
        try {
            for (byte[] record : records) {
                Durable.writeFully(channel, record);
            }
            // force(true): the file's new length is metadata, and must be on disk before the commit says so.
            channel.force(true);
        } catch (IOException | RuntimeException e) {
            // Nothing committed it, so what was written is cut off again, and the next save writes a new file.
            compactNext = true;
            try {
                channel.truncate(fileBytes);
            } catch (IOException | RuntimeException ignored) {
                // A reader drops it anyway: it's after the committed end.
            }
            throw e;
        }
        try {
            commit(fileBytes + appended);
        } catch (IOException | RuntimeException e) {
            // The commit may or may not be on disk, so the save is kept where it is (a reader applies it if it's
            // whole either way) and the next save writes a new file.
            compactNext = true;
            throw e;
        }
        fileBytes += appended;
        appends++;
    }

    /** Writes the other commit slot with the next sequence and {@code end}, and syncs it. */
    private void commit(long end) throws IOException {
        int next = 1 - slot;
        ByteBuffer bytes = ByteBuffer.wrap(NativeVariableFile.commit(sequence + 1, end));
        long position = NativeVariableFile.commitOffset(next);
        while (bytes.hasRemaining()) {
            position += channel.write(bytes, position);
        }
        channel.force(true);
        slot = next;
        sequence++;
    }

    private void compact(List<NativeVariableFile.Op> all) throws IOException {
        closeChannel();
        try {
            write(file, all);
            sequence = 1;
            slot = 0;
            compactNext = false;
            compactions++;
        } catch (IOException | RuntimeException e) {
            compactNext = true;
            throw e;
        } finally {
            try {
                fileBytes = Files.size(file);
                openChannel();
            } catch (IOException | RuntimeException e) {
                // Without a channel to append to, the next save writes a new file.
                closeQuietly();
                compactNext = true;
            }
        }
    }

    /**
     * Writes the ops as a whole new file, committed to its end: a temporary file, synced, then moved over
     * {@code target}.
     */
    static void write(Path target, List<NativeVariableFile.Op> ops) throws IOException {
        List<byte[]> records = NativeVariableFile.records(ops);
        long end = NativeVariableFile.HEADER_LENGTH;
        for (byte[] record : records) {
            end += record.length;
        }
        byte[] header = NativeVariableFile.header(end);
        Durable.replace(target, out -> {
            out.write(header);
            for (byte[] record : records) {
                out.write(record);
            }
        });
    }

    /** Removes temporary files a compaction left when the server stopped in the middle of it. */
    private void removeLeftovers() throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        if (!Files.exists(file) || !Files.isDirectory(dir)) {
            return;
        }
        String prefix = file.getFileName().toString();
        try (var temps = Files.newDirectoryStream(dir, path -> {
            String name = path.getFileName().toString();
            return name.startsWith(prefix) && name.endsWith(".tmp") && Files.isRegularFile(path);
        })) {
            for (Path temp : temps) {
                Files.deleteIfExists(temp);
            }
        }
    }

    /** Opens the file to append to. A seam for tests, which make writes fail. */
    interface Opener {
        FileChannel open(Path file) throws IOException;
    }

    Opener opener = file -> FileChannel.open(file, StandardOpenOption.WRITE);

    private void openChannel() throws IOException {
        FileChannel opened = opener.open(file);
        try {
            opened.position(fileBytes);
        } catch (IOException | RuntimeException e) {
            opened.close();
            throw e;
        }
        channel = opened;
    }

    private void closeQuietly() {
        try {
            closeChannel();
        } catch (IOException ignored) {
            channel = null;
        }
    }

    private void closeChannel() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        // Nothing saves through a closed storage.
        loaded = false;
        closeChannel();
    }
}
