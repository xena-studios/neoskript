/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * File changes that survive a power cut once they return: contents are synced before a rename makes them visible,
 * and the directory is synced after a rename, a copy or a delete, so the change to its entries is on disk too.
 */
final class Durable {

    private Durable() {}

    /** What goes in a file, written to a stream. */
    interface Content {
        void writeTo(OutputStream out) throws IOException;
    }

    /**
     * Streams {@code content} into a temporary file beside {@code target}, syncs it, and moves it over
     * {@code target}. Nothing is held in memory but a small buffer.
     */
    static void replace(Path target, Content content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            try (FileOutputStream file = new FileOutputStream(temp.toFile())) {
                BufferedOutputStream out = new BufferedOutputStream(file, 1 << 16);
                content.writeTo(out);
                out.flush();
                file.getChannel().force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException | Error e) {
            deleteQuietly(temp, e);
            throw e;
        }
        syncDirectory(parent);
    }

    /** Deletes a temporary file after {@code failure}, adding any problem doing so to it rather than hiding it. */
    private static void deleteQuietly(Path temp, Throwable failure) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException | RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    /**
     * Copies {@code from} to a new file {@code to}, synced, with its directory. Only the time it was last changed is
     * copied, not its other attributes: a copy of a read-only file would be read-only too, and couldn't be synced on
     * Windows, which needs write access for that.
     */
    static Path copy(Path from, Path to) throws IOException {
        if (Files.exists(to)) {
            throw new FileAlreadyExistsException(to.toString());
        }
        // Copied to a temporary name and renamed once whole, so a failed copy never looks like a backup. Through a
        // channel opened here, not Files.copy, which gives the copy the source's permissions.
        Path parent = to.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(parent, to.getFileName().toString(), ".tmp");
        try {
            try (FileChannel in = FileChannel.open(from, StandardOpenOption.READ);
                    FileChannel out = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                long size = in.size();
                long done = 0;
                while (done < size) {
                    long copied = out.transferFrom(in, done, size - done);
                    if (copied <= 0) {
                        throw new IOException(from + " got shorter while it was copied");
                    }
                    done += copied;
                }
                out.force(true);
            }
            Files.setLastModifiedTime(temp, Files.getLastModifiedTime(from));
            // Not REPLACE_EXISTING: a backup is never written over.
            Files.move(temp, to);
        } catch (IOException | RuntimeException | Error e) {
            deleteQuietly(temp, e);
            throw e;
        }
        syncDirectory(parent);
        return to;
    }

    /** Deletes {@code file} and syncs its directory, so the delete can't be undone by a power cut. */
    static void delete(Path file) throws IOException {
        Files.delete(file);
        syncDirectory(file.toAbsolutePath().getParent());
    }

    static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    static void writeFully(FileChannel out, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            out.write(buffer);
        }
    }

    /** Syncs a directory's entries, where the system allows opening one (not on Windows, which needs no sync). */
    static void syncDirectory(Path dir) {
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException e) {
            // Best effort.
        }
    }
}
