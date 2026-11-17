/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * Writes files off the server's threads, so a save never holds up a tick. Each file is written whole to a temporary
 * file next to it, flushed to disk, then moved over the old one in one step, so the file is either as it was or as it
 * was saved, never half written, even if the server dies mid-save.
 *
 * <p>Writes to one file happen in the order they were asked for. Several saves of a file before the first is written
 * write only the last, as the others would be overwritten anyway. Anything that reads or deletes a file waits for its
 * writes first ({@link #await}), so scripts see their saves as if they were written at once.
 *
 * <p>Once closed, saves are written straight away, on the thread that asks: scripts still save while the server
 * stops, after this addon has disabled.
 */
final class Saver implements AutoCloseable {

    /** How long a read waits for a file's writes, before going ahead and saying so. */
    private static final long AWAIT_SECONDS = 30;

    private final ExecutorService writer;
    /** What's still to be written to each file: only the last save of it. */
    private final Map<Path, String> pending = new ConcurrentHashMap<>();
    /** The last write asked for, for each file with one not done. */
    private final Map<Path, CompletableFuture<Void>> writing = new ConcurrentHashMap<>();
    /** Told when a write fails: the file and why. */
    private final BiConsumer<Path, IOException> failed;
    private volatile boolean closed;

    Saver(BiConsumer<Path, IOException> failed) {
        this.failed = failed;
        this.writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "NeoSkriptYaml saver");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Writes {@code content} to {@code file}: soon, off this thread, or at once if this is closed. */
    void save(Path file, String content) {
        Path key = file.toAbsolutePath().normalize();
        if (closed) {
            await(key);
            write(key, content);
            return;
        }
        synchronized (this) {
            pending.put(key, content);
            CompletableFuture<Void> previous = writing.getOrDefault(key, CompletableFuture.completedFuture(null));
            CompletableFuture<Void> next;
            try {
                next = previous.thenRunAsync(() -> {
                    String latest = pending.remove(key);
                    if (latest != null) {
                        write(key, latest);
                    }
                }, writer);
            } catch (java.util.concurrent.RejectedExecutionException closing) {
                pending.remove(key);
                write(key, content);
                return;
            }
            writing.put(key, next);
            next.whenComplete((done, error) -> writing.remove(key, next));
        }
    }

    /** Waits for the writes to {@code file} asked for so far. */
    void await(Path file) {
        CompletableFuture<Void> last = writing.get(file.toAbsolutePath().normalize());
        if (last == null) {
            return;
        }
        try {
            last.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException | java.util.concurrent.ExecutionException e) {
            failed.accept(file, new IOException("its save is taking more than " + AWAIT_SECONDS + " seconds"));
        }
    }

    /** Whether any write is still to happen. */
    boolean busy() {
        return !writing.isEmpty();
    }

    /** Writes what's pending, then writes on the calling thread from now on. */
    @Override
    public void close() {
        closed = true;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Anything the writer didn't get to.
        pending.forEach((file, content) -> {
            if (pending.remove(file, content)) {
                write(file, content);
            }
        });
    }

    /** Writes the whole file to a temporary file next to it, then moves it into place. */
    private void write(Path file, String content) {
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
        } catch (IOException | RuntimeException e) {
            failed.accept(file, e instanceof IOException io ? io : new IOException(e.toString(), e));
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
}
