/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Downloads pinned files into a cache, verified by SHA-256. */
public final class Downloads {

    private final Path cacheDir;
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Downloads(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /** Returns the cached file, downloading it first if it's missing or doesn't match the hash. */
    public Path fetch(String url, String sha256, String fileName) throws IOException, InterruptedException {
        Path target = cacheDir.resolve(fileName);
        if (Files.isRegularFile(target) && sha256(target).equalsIgnoreCase(sha256)) {
            return target;
        }
        Files.createDirectories(cacheDir);
        System.out.println("[harness] Downloading " + url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "neoskript-test-harness (https://github.com/xena-studios/neoskript)")
                .build();
        Path partial = cacheDir.resolve(fileName + ".part");
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(partial);
            throw new IOException("Download of " + url + " failed with HTTP " + response.statusCode());
        }
        String actual = sha256(partial);
        if (!actual.equalsIgnoreCase(sha256)) {
            Files.deleteIfExists(partial);
            throw new IOException(
                    "Checksum mismatch for " + url + ": expected " + sha256 + ", got " + actual);
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
