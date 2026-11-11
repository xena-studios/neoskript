/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Unpacks a pinned server's patched jar and libraries into the harness cache, in the same place the
 * harness's servers unpack them, so the harness bot can compile against the server's own classes.
 * The Gradle build runs it with the environment file, the cache directory and a Java to run Paperclip.
 */
public final class ServerClasses {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: ServerClasses <environment.json> <cache dir> <java executable>");
        }
        Environment environment;
        try (Reader reader = Files.newBufferedReader(Path.of(args[0]))) {
            environment = Environment.parse(reader);
        }
        Path cacheDir = Path.of(args[1]);
        Path bundlerDir = unpack(environment, cacheDir, Path.of(args[2]));
        System.out.println("[harness] " + environment.name() + " server classes are in " + bundlerDir);
    }

    /** Unpacks the server unless it already is, and returns the directory with {@code versions/} and {@code libraries/}. */
    static Path unpack(Environment environment, Path cacheDir, Path java) throws IOException, InterruptedException {
        Path bundlerDir = cacheDir.resolve("bundler").resolve(environment.name());
        if (Files.isRegularFile(serverJar(environment, bundlerDir))) {
            return bundlerDir;
        }
        Environment.Download download = environment.download();
        Path paperclip = new Downloads(cacheDir.resolve("downloads"))
                .fetch(download.url(), download.sha256(), environment.name() + ".jar");
        // Paperclip downloads the vanilla server, patches it and unpacks the libraries, then exits
        // without starting the server.
        Process process = new ProcessBuilder(List.of(
                        java.toString(),
                        "-DbundlerRepoDir=" + bundlerDir,
                        "-Dpaperclip.patchonly=true",
                        "-jar",
                        paperclip.toString()))
                .directory(cacheDir.toFile())
                .inheritIO()
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.isRegularFile(serverJar(environment, bundlerDir))) {
            throw new IOException("Paperclip exited with code " + exitCode + " without unpacking "
                    + serverJar(environment, bundlerDir));
        }
        return bundlerDir;
    }

    /** Where Paperclip puts the patched server jar, e.g. {@code versions/26.3/paper-26.3.jar}. */
    static Path serverJar(Environment environment, Path bundlerDir) {
        String version = environment.minecraftVersion();
        String platform = environment.platform().name().toLowerCase(Locale.ROOT);
        return bundlerDir.resolve("versions").resolve(version).resolve(platform + "-" + version + ".jar");
    }

    private ServerClasses() {}
}
