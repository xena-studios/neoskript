/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Prepares a fresh server directory for one scenario run and starts the server in it. */
public final class ServerBuilder {

    private final HarnessConfig config;
    private final Environment environment;
    private final Path serverJar;
    private final Path runDir;
    private final List<Path> plugins = new ArrayList<>();
    private final Map<String, String> files = new LinkedHashMap<>();
    private final List<String> jvmArgs = new ArrayList<>();

    ServerBuilder(HarnessConfig config, Environment environment, Path serverJar, Path runDir) {
        this.config = config;
        this.environment = environment;
        this.serverJar = serverJar;
        this.runDir = runDir;
    }

    public ServerBuilder plugin(Path jar) {
        plugins.add(jar);
        return this;
    }

    /** Writes a file into the server directory before it starts, e.g. {@code plugins/Skript/scripts/x.sk}. */
    public ServerBuilder file(String relativePath, String content) {
        files.put(relativePath, content);
        return this;
    }

    public ServerBuilder jvmArg(String arg) {
        jvmArgs.add(arg);
        return this;
    }

    public Path runDir() {
        return runDir;
    }

    public ServerProcess start() throws IOException {
        Files.createDirectories(runDir.resolve("plugins"));
        Files.writeString(runDir.resolve("eula.txt"), "eula=true\n");
        Files.writeString(runDir.resolve("server.properties"), serverProperties());
        // Test servers must never report to bStats.
        Files.createDirectories(runDir.resolve("plugins/bStats"));
        Files.writeString(runDir.resolve("plugins/bStats/config.yml"), "enabled: false\n");
        for (Path plugin : plugins) {
            Files.copy(plugin, runDir.resolve("plugins").resolve(plugin.getFileName()));
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = runDir.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }

        List<String> command = new ArrayList<>();
        command.add(config.javaExecutable(environment.javaVersion()).toString());
        command.add("-Xms1G");
        command.add("-Xmx2G");
        command.add("-Ddisable.watchdog=true");
        command.add("-Duser.language=en");
        command.add("-Duser.country=US");
        // Paperclip unpacks the vanilla server and libraries here, so it only happens once per environment.
        command.add("-DbundlerRepoDir=" + config.cacheDir().resolve("bundler").resolve(environment.name()));
        command.addAll(jvmArgs);
        command.add("-jar");
        command.add(serverJar.toString());
        command.add("--nogui");
        return ServerProcess.start(command, runDir, config.echo());
    }

    private static String serverProperties() {
        return """
                online-mode=false
                server-port=%d
                motd=NeoSkript test harness
                level-type=minecraft\\:flat
                generator-settings={"layers"\\:[{"block"\\:"minecraft\\:bedrock","height"\\:1},{"block"\\:"minecraft\\:stone","height"\\:3},{"block"\\:"minecraft\\:grass_block","height"\\:1}],"biome"\\:"minecraft\\:plains"}
                generate-structures=false
                spawn-protection=0
                view-distance=4
                simulation-distance=4
                spawn-monsters=false
                spawn-npcs=false
                enable-query=false
                enable-rcon=false
                """.formatted(freePort());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
