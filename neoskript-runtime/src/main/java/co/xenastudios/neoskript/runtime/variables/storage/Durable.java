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

    /**
     * Whether this is Windows, where renaming over a file another handle has open can be refused, so a backup that
     * copies without its storage's lock could make a save fail.
     */
    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");

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
        try (FileChannel in = FileChannel.open(from, StandardOpenOption.READ)) {
            return copy(in, null, in.size(), to, () -> false, Files.getLastModifiedTime(from));
        }
    }

    /**
     * What a copy to {@code to} is written as until it's whole: always this name, so one a crash left is found by it
     * exactly, and never one an owner would give a copy of their own ({@code variables.csv.tmp}).
     */
    static Path temporary(Path to) {
        return to.resolveSibling(to.getFileName() + TEMPORARY);
    }

    /** What {@link #temporary} adds to a copy's name. */
    static final String TEMPORARY = ".copying.tmp";

    /**
     * Copies the first {@code size} bytes of {@code in} to a new file {@code to}, as {@link #copy(Path, Path)}, with
     * {@code head}, if given, in place of the bytes at its start: what they were when {@code in} was opened, for a file
     * whose header changes while it's copied. The copy is written to {@link #temporary} and renamed once whole, and
     * {@code cancelled} is checked every few megabytes: once it's true, the copy stops, is removed and throws.
     */
    static Path copy(FileChannel in, byte[] head, long size, Path to, java.util.function.BooleanSupplier cancelled,
            java.nio.file.attribute.FileTime modified) throws IOException {
        if (Files.exists(to)) {
            throw new FileAlreadyExistsException(to.toString());
        }
        // Copied to a temporary name and renamed once whole, so a failed copy never looks like a backup. Through a
        // channel opened here, not Files.copy, which gives the copy the source's permissions.
        Path parent = to.toAbsolutePath().getParent();
        Path temp = temporary(to);
        FileChannel created;
        try {
            // CREATE_NEW, never deleting what's there: another copy may be writing this temporary name right now, and
            // a crash's leftovers are removed at startup. Taken, it counts as the name being taken, so the caller
            // numbers the next one.
            created = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException busy) {
            throw new FileAlreadyExistsException(to.toString());
        }
        try {
            try (FileChannel out = created) {
                long done = 0;
                if (head != null) {
                    writeFully(out, head);
                    done = head.length;
                }
                while (done < size) {
                    if (cancelled.getAsBoolean()) {
                        throw new IOException("The copy to " + to.getFileName() + " was cut off");
                    }
                    long copied = out.transferFrom(in.position(done), done, Math.min(size - done, CHUNK));
                    if (copied <= 0) {
                        throw new IOException(to.getFileName() + "'s source got shorter while it was copied");
                    }
                    done += copied;
                }
                out.force(true);
            }
            Files.setLastModifiedTime(temp, modified);
            // Not REPLACE_EXISTING: a backup is never written over.
            Files.move(temp, to);
        } catch (IOException | RuntimeException | Error e) {
            deleteQuietly(temp, e);
            throw e;
        }
        syncDirectory(parent);
        return to;
    }

    /** How much is copied between checks that the copy hasn't been cut off. */
    private static final long CHUNK = 8L << 20;

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
