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
import java.util.LinkedHashMap;
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
    private byte[] fileId;
    private Path recoveryBackup;
    private long recoveredBytes;
    /** Why the file must be backed up before the next compaction writes over it, or {@code null}. */
    private String backupBeforeCompacting;
    private boolean recover;
    private final List<String> notes = new ArrayList<>();
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
     * Where the file was backed up before a save a crash cut off was dropped from it, or before a recovery, else
     * {@code null}. Made by the first save after loading, not by the load, which doesn't write.
     */
    public Path recoveryBackup() {
        return recoveryBackup;
    }

    /** How many bytes after the last whole save the last load found: a save a crash cut off, dropped by the next save. */
    public long recoveredBytes() {
        return recoveredBytes;
    }

    /**
     * Whether the next load may recover from damage no snapshot can repair: it loads everything up to the last good
     * save, backs the damaged file up, writes a new one, and says what was lost ({@code variables.recover}).
     */
    public synchronized void recover(boolean recover) {
        this.recover = recover;
    }

    /** What the last load or saves did that the console should hear about, once. */
    @Override
    public synchronized List<String> notes() {
        List<String> drained = List.copyOf(notes);
        notes.clear();
        return drained;
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
        backupBeforeCompacting = null;
        removeLeftovers();
        if (!Files.exists(file)) {
            // Nothing written yet: the first save makes the file.
            fileBytes = 0;
            sequence = 0;
            slot = 0;
            fileId = null;
            compactNext = false;
            savedAt = store.modifications();
            loaded = true;
            return new VariableStorage.Result(0, 0, 0);
        }
        NativeVariableFile.Contents contents = contents();
        try {
            return loadFrom(contents);
        } catch (IOException | RuntimeException e) {
            closeQuietly();
            throw e;
        }
    }

    /** The file's contents, repaired from its snapshot, or recovered, if it's damaged and that's possible. */
    private NativeVariableFile.Contents contents() throws IOException {
        byte[] data = NativeVariableFile.readFile(file);
        try {
            return NativeVariableFile.read(data);
        } catch (NativeVariableFile.Damaged damaged) {
            byte[] repaired = repair(file, data, damaged, notes);
            if (repaired != null) {
                try {
                    NativeVariableFile.Contents contents = NativeVariableFile.read(repaired);
                    // The file on disk is still damaged: the first save writes a new one, after backing it up.
                    backupBeforeCompacting = "repaired from a snapshot";
                    compactNext = true;
                    return contents;
                } catch (NativeVariableFile.Damaged more) {
                    data = repaired;
                    damaged = more;
                }
            }
            if (!recover) {
                throw refusal(damaged);
            }
            return recoverFrom(data, damaged);
        }
    }

    /**
     * {@code data} with the range a snapshot of the same file covers put back from it, if the damage is in that
     * range and the commit after it could be read; else {@code null}. Nothing is lost: until the next compaction
     * the snapshot is byte for byte the start of the file.
     */
    static byte[] repair(Path file, byte[] data, NativeVariableFile.Damaged damaged, List<String> notes)
            throws IOException {
        Snapshots.Snapshot snapshot = Snapshots.find(file, damaged.fileId());
        if (snapshot == null || damaged.committed() < snapshot.end() || damaged.position() >= snapshot.end()
                || data.length < snapshot.end()) {
            return null;
        }
        byte[] repaired = data.clone();
        int from = NativeVariableFile.HEADER_LENGTH;
        System.arraycopy(snapshot.bytes(), from, repaired, from, (int) snapshot.end() - from);
        notes.add(file.getFileName() + " was damaged at byte " + damaged.position() + ", inside what its snapshot "
                + file.toAbsolutePath().getParent().relativize(snapshot.path()) + " covers (up to byte "
                + snapshot.end() + "). That part was taken from the snapshot and the saves after it from the file, "
                + "so nothing was lost. The next save writes a new file, after backing up the damaged one.");
        return repaired;
    }

    /** Reads {@code file}, repaired from its snapshot if it's damaged inside the snapshot's range. */
    static NativeVariableFile.Contents readRepaired(Path file, List<String> log) throws IOException {
        byte[] data = NativeVariableFile.readFile(file);
        try {
            return NativeVariableFile.read(data);
        } catch (NativeVariableFile.Damaged damaged) {
            byte[] repaired = repair(file, data, damaged, log);
            if (repaired == null) {
                throw damaged;
            }
            return NativeVariableFile.read(repaired);
        }
    }

    private IOException refusal(NativeVariableFile.Damaged damaged) throws IOException {
        Path newest = Snapshots.newestGood(file);
        String snapshot = newest == null ? "There's no snapshot of it in backups/snapshots."
                : "The newest snapshot is " + file.toAbsolutePath().getParent().relativize(newest)
                        + ", which doesn't cover the damage (it's from before the saves after it).";
        return new IOException(file.getFileName() + " is " + damaged.getMessage() + ". NeoSkript didn't load it and "
                + "won't write over it. " + snapshot + " To load everything up to the last good save, set "
                + "variables.recover to true in config.yml and restart: the file is backed up first, and what's lost "
                + "is listed. Or restore the file from your own backups.", damaged);
    }

    /**
     * Loads everything up to the last good save of a damaged file, backs the file up, and writes a new one at once.
     * Says what was lost.
     */
    private NativeVariableFile.Contents recoverFrom(byte[] data, NativeVariableFile.Damaged damaged)
            throws IOException {
        int[] kept = {0};
        long[] stopped = {0};
        LinkedHashMap<String, VariableFile.Entry> entries = NativeVariableFile.salvage(data, kept, stopped);
        long end = damaged.committed() > 0 ? damaged.committed() : data.length;
        int[] after = {0};
        List<String> changed = NativeVariableFile.readableAfter(data, stopped[0], end, after);
        Path backup = VariableStorage.backup(file);
        recoveryBackup = backup;
        List<NativeVariableFile.Op> ops = new ArrayList<>(entries.size());
        VariableStore sorted = new VariableStore();
        for (VariableFile.Entry entry : entries.values()) {
            sorted.set(entry.name(), new RawValue(entry.type(), entry.bytes()));
        }
        sorted.forEach((name, value) -> {
            RawValue raw = (RawValue) value;
            ops.add(new NativeVariableFile.Op(name, raw.type(), raw.bytes()));
        });
        Written written = write(file, ops);
        if (written.problem() != null) {
            notes.add(written.problem());
        }
        java.util.Set<String> distinct = new java.util.LinkedHashSet<>(changed);
        String names = String.join(", ", distinct.stream().limit(20).toList()) + (distinct.size() > 20 ? ", ..." : "");
        notes.add("Recovered " + file.getFileName() + " (variables.recover): " + damaged.getMessage() + ". Loaded "
                + entries.size() + " variables from the " + kept[0] + " good save" + (kept[0] == 1 ? "" : "s") + " before byte "
                + stopped[0] + ". Lost: "
                + "the committed saves from byte " + stopped[0] + " to byte " + end + " (" + (end - stopped[0])
                + " bytes)" + (after[0] == 0 ? ", which can't be read." : "; " + after[0] + " of them after the damaged "
                + "record could still be read, and changed " + distinct.size() + " variables: " + names + ". Those "
                + "variables are as they were in the last good save.") + " The damaged file is backed up as "
                + file.toAbsolutePath().getParent().relativize(backup) + ", and a new file was written.");
        return NativeVariableFile.read(NativeVariableFile.readFile(file));
    }

    private VariableStorage.Result loadFrom(NativeVariableFile.Contents contents) throws IOException {
        if (contents.damaged()) {
            // A save a crash cut off after the last commit. Nothing's written now: the first save drops it, after
            // backing the file up.
            recoveredBytes = contents.length() - contents.validEnd();
            backupBeforeCompacting = "a save a crash cut off";
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
        if (!skipped.isEmpty()) {
            // As for variables.csv: values that can't be read are skipped. The first save writes the file without
            // them, after backing it up, so they aren't skipped again on every start.
            backupBeforeCompacting = "values that couldn't be read";
        }
        // A compacted file is in Skript's order, which the store takes in one pass.
        store.load(names, values);
        fileBytes = contents.validEnd();
        sequence = contents.sequence();
        slot = contents.slot();
        fileId = contents.fileId();
        // Anything to tidy (a cut-off save, a save whose commit didn't happen, values skipped, a repair, or a file
        // that's grown past its limit) is done by the first save, which writes a new file. Loading writes nothing.
        compactNext = compactNext || backupBeforeCompacting != null || contents.validEnd() != contents.committed()
                || contents.records() > 1 && fileBytes > 2 * liveBytes + COMPACT_SLACK;
        openChannel();
        savedAt = store.modifications();
        loaded = true;
        return new VariableStorage.Result(values.size(), raw, 0, List.copyOf(skipped), null, 0);
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
        ByteBuffer bytes = ByteBuffer.wrap(NativeVariableFile.commit(fileId, sequence + 1, end));
        long position = NativeVariableFile.commitOffset(next);
        while (bytes.hasRemaining()) {
            position += channel.write(bytes, position);
        }
        channel.force(true);
        slot = next;
        sequence++;
    }

    private void compact(List<NativeVariableFile.Op> all) throws IOException {
        if (backupBeforeCompacting != null && Files.exists(file)) {
            recoveryBackup = VariableStorage.backup(file);
            notes.add("Backed up " + file.getFileName() + " as " + file.toAbsolutePath().getParent()
                    .relativize(recoveryBackup) + " (" + backupBeforeCompacting + ") before writing a new one.");
            backupBeforeCompacting = null;
        }
        closeChannel();
        try {
            Written written = write(file, all);
            if (written.problem() != null) {
                notes.add(written.problem());
            }
            fileId = written.fileId();
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

    /** A file written whole: its new id, and why its snapshot couldn't be made, or {@code null}. */
    record Written(byte[] fileId, String problem) {}

    /**
     * Writes the ops as a whole new file with a new id, committed to its end: a temporary file, synced, then moved
     * over {@code target}. Then copies it to the snapshots in {@code backups/}; if that fails the file is still
     * written, and the problem is returned.
     */
    static Written write(Path target, List<NativeVariableFile.Op> ops) throws IOException {
        List<byte[]> records = NativeVariableFile.records(ops);
        long end = NativeVariableFile.HEADER_LENGTH;
        for (byte[] record : records) {
            end += record.length;
        }
        byte[] id = NativeVariableFile.newId();
        byte[] header = NativeVariableFile.header(id, end);
        Durable.replace(target, out -> {
            out.write(header);
            for (byte[] record : records) {
                out.write(record);
            }
        });
        try {
            Snapshots.save(target);
            return new Written(id, null);
        } catch (IOException | RuntimeException e) {
            return new Written(id, "Couldn't keep a snapshot of " + target.getFileName() + " in backups/snapshots: "
                    + e + ". The variables are saved; a snapshot only helps repair damage later.");
        }
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
