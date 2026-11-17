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

    /**
     * The largest file a save writes: what a reader accepts ({@link NativeVariableFile#MAX_LENGTH}). Lower in tests.
     */
    static volatile long maxLength = NativeVariableFile.MAX_LENGTH;

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
    private final VariableStore loadInto;
    private final java.util.function.Predicate<String> owns;
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
        this(store, codecs, file, Routing.ALL);
    }

    /** One of several databases: see {@link VariableStorage#VariableStorage(VariableStore, VariableCodecs, Path, Routing)}. */
    public NativeVariableStorage(VariableStore store, VariableCodecs codecs, Path file, Routing routing) {
        this.store = store;
        this.loadInto = routing.loadInto() != null ? routing.loadInto() : store;
        this.owns = routing.owns();
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
        VariableStorage.removeBackupLeftovers(file);
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
        Opened opened = open(file, recover, notes);
        if (opened.repaired()) {
            // The file on disk is still damaged: the first save writes a new one, after backing it up.
            backupBeforeCompacting = "repaired from a snapshot";
            compactNext = true;
        }
        if (opened.salvaged() == null) {
            return opened.contents();
        }
        // Recovered: the damaged file is backed up, and a new one is written now, from what was salvaged.
        recoveryBackup = opened.backup();
        Written written = write(file, inOrder(opened.salvaged().values()));
        if (written.problem() != null) {
            notes.add(written.problem());
        }
        return NativeVariableFile.read(NativeVariableFile.readFile(file));
    }

    /**
     * What reading a native file gave: its contents (repaired from a snapshot if {@code repaired}), or, after a
     * recovery, the variables salvaged from the saves before the damage and where the damaged file was backed up.
     */
    record Opened(NativeVariableFile.Contents contents, boolean repaired,
            LinkedHashMap<String, VariableFile.Entry> salvaged, Path backup) {

        /** The variables, whichever way they were read. */
        java.util.Map<String, VariableFile.Entry> entries() {
            return salvaged != null ? salvaged : contents.entries();
        }
    }

    /**
     * Reads a native file for loading or for moving its variables to {@code variables.csv}. Damage inside the newest
     * snapshot's range is repaired from it. Other damage is refused with a message saying what to do, unless
     * {@code recover}: then the file is backed up and the saves before the damage are salvaged, and what's lost is
     * added to {@code notes}. Nothing is written but the backup.
     */
    static Opened open(Path file, boolean recover, List<String> notes) throws IOException {
        byte[] data = NativeVariableFile.readFile(file);
        NativeVariableFile.Damaged damaged;
        try {
            return new Opened(NativeVariableFile.read(data), false, null, null);
        } catch (NativeVariableFile.Damaged e) {
            damaged = e;
        }
        byte[] repaired = repair(file, data, damaged, notes);
        if (repaired != null) {
            try {
                return new Opened(NativeVariableFile.read(repaired), true, null, null);
            } catch (NativeVariableFile.Damaged more) {
                data = repaired;
                damaged = more;
            }
        }
        if (!recover) {
            throw refusal(file, damaged);
        }
        return salvage(file, data, damaged, notes);
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

    static IOException refusal(Path file, NativeVariableFile.Damaged damaged) throws IOException {
        Path newest = Snapshots.newestGood(file);
        Path dir = file.toAbsolutePath().getParent();
        String snapshot = newest == null ? "There's no snapshot of it in backups/snapshots."
                : "The newest snapshot is " + dir.relativize(newest) + ", which doesn't cover the damage (it's from "
                        + "before the saves after it).";
        return new IOException(file.getFileName() + " is " + damaged.getMessage() + ". NeoSkript didn't load it and "
                + "won't write over it. " + snapshot + " To load everything up to the last good save: set "
                + "variables.recover to true in config.yml and restart (with variables.storage still as it is now). "
                + "The damaged file is backed up first, what's lost is listed, and the switch turns itself off. Or "
                + "restore the file from your own backups.", damaged);
    }

    /** Backs up a damaged file and salvages the saves before the damage, saying what's lost. */
    private static Opened salvage(Path file, byte[] data, NativeVariableFile.Damaged damaged, List<String> notes)
            throws IOException {
        int[] kept = {0};
        long[] stopped = {0};
        LinkedHashMap<String, VariableFile.Entry> entries = NativeVariableFile.salvage(data, damaged.fileId(), kept, stopped);
        long end = damaged.committed() > 0 ? damaged.committed() : data.length;
        int[] readable = {0};
        List<String> changed = NativeVariableFile.readableAfter(data, damaged.fileId(), stopped[0], end, readable);
        Path backup = VariableStorage.backup(file);
        java.util.Set<String> distinct = new java.util.LinkedHashSet<>(changed);
        String names = String.join(", ", distinct.stream().limit(20).toList()) + (distinct.size() > 20 ? ", ..." : "");
        notes.add("Recovered " + file.getFileName() + " (variables.recover): " + damaged.getMessage() + ". Kept "
                + entries.size() + " variable" + (entries.size() == 1 ? "" : "s") + " from the " + kept[0] + " good save" + (kept[0] == 1 ? "" : "s")
                + " before byte " + stopped[0] + ". Lost: the committed saves from byte " + stopped[0] + " to byte " + end
                + " (" + (end - stopped[0]) + " bytes)" + (readable[0] == 0 ? ", none of which can be read."
                        : "; " + readable[0] + " of their records could still be read, and changed " + distinct.size()
                                + " variables: " + names + ". Those variables are as they were in the last good save.")
                + " The damaged file is backed up as " + file.toAbsolutePath().getParent().relativize(backup) + ".");
        return new Opened(null, false, entries, backup);
    }

    /** Entries as ops in Skript's order, through a store of their saved bytes. */
    static List<NativeVariableFile.Op> inOrder(Iterable<VariableFile.Entry> entries) {
        VariableStore sorted = new VariableStore();
        for (VariableFile.Entry entry : entries) {
            sorted.set(entry.name(), new RawValue(entry.type(), entry.bytes()));
        }
        List<NativeVariableFile.Op> ops = new ArrayList<>();
        sorted.forEach((name, value) -> {
            RawValue raw = (RawValue) value;
            ops.add(new NativeVariableFile.Op(name.toLowerCase(java.util.Locale.ROOT), raw.type(), raw.bytes()));
        });
        return ops;
    }

    private VariableStorage.Result loadFrom(NativeVariableFile.Contents contents) throws IOException {
        if (contents.damaged()) {
            // A save a crash cut off after the last commit. Nothing's written now: the first save drops it, after
            // backing the file up.
            recoveredBytes = contents.length() - contents.validEnd();
            if (backupBeforeCompacting == null) {
                backupBeforeCompacting = "a save a crash cut off";
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
        if (!skipped.isEmpty()) {
            // As for variables.csv: values that can't be read are skipped. The first save writes the file without
            // them, after backing it up, so they aren't skipped again on every start.
            if (backupBeforeCompacting == null) {
                backupBeforeCompacting = "values that couldn't be read";
            }
        }
        // A compacted file is in Skript's order, which the store takes in one pass.
        loadInto.load(names, values);
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
        List<String> kept = new ArrayList<>();
        List<String> notSaved = new ArrayList<>();
        store.forEach((variable, value) -> {
            if (variable.startsWith("-") || !owns.test(variable)) {
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
            VariableCodecs.Encoded encoded;
            try {
                encoded = codecs.encode(value);
            } catch (RuntimeException | LinkageError failed) {
                // A codec that fails, or whose classes are gone: the variable keeps what was saved before, as in
                // variables.csv, and the rest are saved as usual.
                if (last != null) {
                    last.seen = current;
                    all.add(new NativeVariableFile.Op(name, last.type, last.bytes));
                    kept.add(name);
                } else {
                    notSaved.add(name);
                }
                return;
            }
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
            List<byte[]> records = NativeVariableFile.records(changes, fileId);
            long appended = 0;
            for (byte[] record : records) {
                appended += record.length;
            }
            // Compacted before it would grow past what a reader accepts, too.
            if (compactNext || channel == null || fileBytes + appended > 2 * live + COMPACT_SLACK
                    || fileBytes + appended > maxLength) {
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
        return new VariableStorage.Result(all.size(), counts[0], counts[1], List.of(), null, 0, List.copyOf(kept),
                List.copyOf(notSaved));
    }

    /**
     * Replaces each value whose saving needs code {@code loader} loaded with its saved bytes, as a {@link RawValue},
     * for an addon that's being disabled, as {@link VariableStorage#detach} does: a value whose codec fails now keeps
     * the bytes last saved for it; one with none stays as it is, and isn't saved. The addon's codecs are then
     * dropped.
     */
    @Override
    public synchronized VariableStorage.Detached detach(ClassLoader loader) {
        Map<String, Object> found = new java.util.LinkedHashMap<>();
        store.forEach((name, value) -> {
            if (codecs.usesCodeOf(value, loader)) {
                found.put(name, value);
            }
        });
        List<String> kept = new ArrayList<>();
        List<String> notSaved = new ArrayList<>();
        for (Map.Entry<String, Object> entry : found.entrySet()) {
            VariableCodecs.Encoded encoded;
            try {
                encoded = codecs.encode(entry.getValue());
            } catch (RuntimeException | LinkageError e) {
                encoded = null;
            }
            if (encoded == null) {
                Saved before = saved.get(entry.getKey().toLowerCase(Locale.ROOT));
                if (before == null) {
                    notSaved.add(entry.getKey());
                    continue;
                }
                encoded = new VariableCodecs.Encoded(before.type, before.bytes);
            }
            store.set(entry.getKey(), new RawValue(encoded.type(), encoded.bytes()));
            kept.add(entry.getKey());
        }
        codecs.forget(loader);
        return new VariableStorage.Detached(List.copyOf(kept), List.copyOf(notSaved));
    }

    /**
     * Holds this storage's lock only to open the file and read its header: a save appends after the end and then
     * rewrites a commit in the header, and a compaction replaces the whole file in one step, so the bytes up to the
     * size read here, under the header read here, are one whole committed file however long the copy takes. Saves,
     * the server's last one included, don't wait for it. Except on Windows, where a rename over a file that's open can
     * be refused: there the copy holds the lock throughout.
     */
    @Override
    public Path backupNow() throws IOException {
        if (Durable.WINDOWS) {
            // On Windows a compaction's rename over the file can be refused while the copy has it open, so saves wait
            // for the copy there; stopping the server cancels it.
            synchronized (this) {
                return copyForBackup();
            }
        }
        return copyForBackup();
    }

    private Path copyForBackup() throws IOException {
        if (backupCancelled) {
            return null;
        }
        java.nio.channels.FileChannel in;
        byte[] head;
        long size;
        java.nio.file.attribute.FileTime modified;
        synchronized (this) {
            try {
                in = java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.READ);
            } catch (java.nio.file.NoSuchFileException notYet) {
                return null;
            }
            try {
                size = in.size();
                head = new byte[(int) Math.min(size, NativeVariableFile.HEADER_LENGTH)];
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(head);
                while (buffer.hasRemaining() && in.read(buffer, buffer.position()) > 0) {
                    // Read on.
                }
                modified = Files.getLastModifiedTime(file);
            } catch (IOException | RuntimeException e) {
                in.close();
                throw e;
            }
        }
        try (in) {
            return VariableStorage.backup(file, VariableStorage.SCHEDULED,
                    target -> Durable.copy(in, head, size, target, () -> backupCancelled, modified));
        }
    }

    @Override
    public void cancelBackup() {
        backupCancelled = true;
    }

    private volatile boolean backupCancelled;

    @Override
    public synchronized VariableFile.Entry previous(String name) {
        Saved before = saved.get(name.toLowerCase(Locale.ROOT));
        return before == null ? null : new VariableFile.Entry(name.toLowerCase(Locale.ROOT), before.type, before.bytes);
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
        byte[] id = NativeVariableFile.newId();
        List<byte[]> records = NativeVariableFile.records(ops, id);
        long end = NativeVariableFile.HEADER_LENGTH;
        for (byte[] record : records) {
            end += record.length;
        }
        // One more than any file written here before, so snapshots are ordered without the clock.
        if (end > maxLength) {
            // A file no reader would accept would lock the server out at the next start: keep the old one.
            throw new IOException("The variables need " + end / (1 << 20) + " MiB, more than the " + maxLength / (1 << 20)
                    + " MiB a native variable file can hold, so they weren't saved and " + target.getFileName()
                    + " is as it was. Delete variables you don't need, or move to csv storage.");
        }
        byte[] header = NativeVariableFile.header(id, Snapshots.latestGeneration(target) + 1, end);
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
