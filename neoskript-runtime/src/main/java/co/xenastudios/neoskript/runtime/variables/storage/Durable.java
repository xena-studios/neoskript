/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
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

    /** Writes {@code chunks} to a temporary file beside {@code target}, syncs it, and moves it over {@code target}. */
    static void replace(Path target, byte[]... chunks) throws IOException {
        replace(target, java.util.List.of(chunks));
    }

    static void replace(Path target, Iterable<byte[]> chunks) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            try (FileChannel out = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (byte[] chunk : chunks) {
                    writeFully(out, chunk);
                }
                out.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        syncDirectory(parent);
    }

    /** Copies {@code from} to a new file {@code to}, synced, with its directory. */
    static Path copy(Path from, Path to) throws IOException {
        Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
        force(to);
        syncDirectory(to.toAbsolutePath().getParent());
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
