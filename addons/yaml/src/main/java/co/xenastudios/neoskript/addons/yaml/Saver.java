/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Writes files off the server's threads, so a save never holds up a tick. Each file is written whole to a temporary
 * file next to it, flushed to disk, moved over the old one in one step, and its folder synced, so the file is either
 * as it was or as it was saved, even if the server dies mid-save.
 *
 * <p><b>Order.</b> Each save carries its snapshot's number, taken when the file's contents were copied. Of a file's
 * saves, only the newest is written: one with an older number is dropped, even if it arrives after a newer one (a
 * region thread that took its snapshot first can finish turning it into text last). Saves of a file not yet written
 * when another comes are dropped too, as the newer one would overwrite them anyway.
 *
 * <p><b>Threads.</b> A few threads write, one write to a file at a time, so a big file only holds up its own saves.
 * {@link #await} writes a file's pending save on the calling thread, so loading, checking or deleting a file sees its
 * saves at once, waiting at most for the one write to that file already under way.
 *
 * <p><b>Closing.</b> {@link #close} stops taking saves for the threads, writes every pending one on the closing thread,
 * and never interrupts a write. After it, saves are written straight away, on the thread that asks: scripts still save
 * while the server stops, after this addon has disabled.
 *
 * <p><b>Links.</b> A file that's a symbolic link is written where it points, so the link stays a link and a file shared
 * through it stays shared.
 */
final class Saver implements AutoCloseable {

    private static final int THREADS = 4;
    /** How long closing waits for the writing threads to finish, after it has written what's pending itself. */
    private static final long CLOSE_SECONDS = 10;
    private static final boolean WINDOWS = File.separatorChar == '\\';

    /** A file's saves: what's still to be written, and the newest written. */
    private static final class Target {
        /** Held while the file is written: one write to it at a time. */
        final Object writing = new Object();
        // Guarded by this.
        long pendingSequence;
        String pendingContent;
        Runnable pendingFailed;
        long writtenSequence;
        boolean scheduled;
    }

    private final ExecutorService writers;
    private final Map<Path, Target> targets = new ConcurrentHashMap<>();
    /** Told when a write fails: the file and why. */
    private final BiConsumer<Path, IOException> failed;
    /** Guarded by this: once set, nothing more goes to the writing threads. */
    private boolean closed;

    Saver(BiConsumer<Path, IOException> failed) {
        this.failed = failed;
        AtomicInteger count = new AtomicInteger();
        this.writers = Executors.newFixedThreadPool(THREADS, task -> {
            Thread thread = new Thread(task, "NeoSkriptYaml saver " + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Writes {@code content} to {@code file} soon, off this thread (or at once if this is closed), unless a save of the
     * file with a newer {@code sequence} came first.
     * @param onFailure run if this save's write fails, as to mark the file changed again
     */
    void save(Path file, String content, long sequence, Runnable onFailure) {
        Path key = key(file);
        Target target = targets.computeIfAbsent(key, k -> new Target());
        boolean schedule;
        synchronized (target) {
            if (sequence <= target.pendingSequence || sequence <= target.writtenSequence) {
                return;
            }
            target.pendingSequence = sequence;
            target.pendingContent = content;
            target.pendingFailed = onFailure;
            schedule = !target.scheduled;
            target.scheduled = true;
        }
        synchronized (this) {
            if (!closed) {
                if (schedule) {
                    writers.execute(() -> flush(key, target));
                }
                return;
            }
        }
        flush(key, target);
    }

    /** Writes {@code file}'s pending save now, on this thread, after any write of it under way. */
    void await(Path file) {
        Path key = key(file);
        Target target = targets.get(key);
        if (target != null) {
            flush(key, target);
        }
    }

    /** Stops using the writing threads, and writes every pending save on this thread. */
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        // Lets a write under way finish; never interrupted, so what it writes isn't lost.
        writers.shutdown();
        targets.forEach(this::flush);
        try {
            writers.awaitTermination(CLOSE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Writes the newest pending save of a file, if there is one. */
    private void flush(Path key, Target target) {
        synchronized (target.writing) {
            String content;
            Runnable onFailure;
            synchronized (target) {
                target.scheduled = false;
                content = target.pendingContent;
                onFailure = target.pendingFailed;
                if (content == null) {
                    return;
                }
                target.writtenSequence = target.pendingSequence;
                target.pendingContent = null;
                target.pendingFailed = null;
            }
            try {
                write(key, content);
            } catch (IOException e) {
                failed.accept(key, e);
                if (onFailure != null) {
                    onFailure.run();
                }
            }
        }
    }

    /** Where a file's saves go: the file a link points to, so the link stays a link. */
    private static Path key(Path file) {
        Path path = file.toAbsolutePath().normalize();
        try {
            if (Files.exists(path)) {
                return path.toRealPath();
            }
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent)) {
                return parent.toRealPath().resolve(path.getFileName());
            }
        } catch (IOException | RuntimeException unresolved) {
            // Written where it's named.
        }
        return path;
    }

    /** Writes the whole file to a temporary file next to it, then moves it into place and syncs the folder. */
    static void write(Path file, String content) throws IOException {
        Path temporary = null;
        try {
            Path folder = file.getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            // Not createTempFile, whose file only its owner can read: the file keeps the permissions it had.
            temporary = file.resolveSibling("." + file.getFileName() + "." + java.util.UUID.randomUUID() + ".tmp");
            Files.createFile(temporary);
            if (Files.exists(file)) {
                try {
                    Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(file));
                } catch (UnsupportedOperationException | IOException notPosix) {
                    // Windows, or a file system without them: the folder's defaults.
                }
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(content);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notHere) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            if (folder != null) {
                syncFolder(folder);
            }
        } catch (RuntimeException e) {
            throw new IOException(e.toString(), e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Nothing more to do: the file itself is as it was.
                }
            }
        }
    }

    /** Syncs a folder's entries, so the rename survives a power cut, as NeoSkript's own files do; not on Windows. */
    private static void syncFolder(Path folder) {
        if (WINDOWS) {
            return;
        }
        try (FileChannel channel = FileChannel.open(folder, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException bestEffort) {
            // Best effort, as Durable.syncDirectory.
        }
    }
}
