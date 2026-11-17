/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * A Velocity proxy in front of harness servers, pinned by SHA-256 as servers are. It starts from Velocity's own
 * default config, with the servers, forced hosts (so a bot joining for {@code b.test} lands on server {@code b}),
 * offline mode and BungeeCord's plugin message channel, which it has on by default.
 */
public final class VelocityProxy {

    private static final String URL = "https://fill-data.papermc.io/v1/objects/"
            + "0844bcabdcdb4f5b875906fd4c3365bc1bb76918e42bc595ed88ad195258dd75/velocity-4.3.1-45.jar";
    private static final String SHA256 = "0844bcabdcdb4f5b875906fd4c3365bc1bb76918e42bc595ed88ad195258dd75";
    /** Velocity has started. */
    public static final Pattern DONE = Pattern.compile("Done \\([0-9.,]+s\\)!");

    private VelocityProxy() {}

    /**
     * Starts Velocity in {@code dir} (emptied first).
     *
     * @param servers each server's name and port, the first being where players go without a forced host
     */
    public static ServerProcess start(ScenarioContext context, Path dir, int port, Map<String, Integer> servers)
            throws IOException, InterruptedException {
        Path jar = context.download(URL, SHA256, "velocity-4.3.1-45.jar");
        ScenarioContext.deleteRecursively(dir);
        Files.createDirectories(dir);
        String config;
        try (ZipFile zip = new ZipFile(jar.toFile()); InputStream in = zip.getInputStream(zip.getEntry("default-velocity.toml"))) {
            config = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        config = config.replaceFirst("(?m)^bind = .*$", "bind = \"127.0.0.1:" + port + "\"")
                .replaceFirst("(?m)^online-mode = .*$", "online-mode = false")
                .replaceFirst("(?m)^force-key-authentication = .*$", "force-key-authentication = false")
                .replaceFirst("(?m)^login-ratelimit = .*$", "login-ratelimit = 0");
        StringBuilder serverList = new StringBuilder("[servers]\n");
        StringBuilder forced = new StringBuilder("[forced-hosts]\n");
        servers.forEach((name, serverPort) -> {
            serverList.append(name).append(" = \"127.0.0.1:").append(serverPort).append("\"\n");
            forced.append('"').append(name).append(".test\" = [\"").append(name).append("\"]\n");
        });
        serverList.append("try = [\"").append(servers.keySet().iterator().next()).append("\"]\n");
        config = Pattern.compile("(?ms)^\\[servers\\].*?^(?=\\[forced-hosts\\])").matcher(config)
                .replaceFirst(java.util.regex.Matcher.quoteReplacement(serverList + "\n"));
        config = Pattern.compile("(?ms)^\\[forced-hosts\\].*?^(?=\\[advanced\\])").matcher(config)
                .replaceFirst(java.util.regex.Matcher.quoteReplacement(forced + "\n"));
        Files.writeString(dir.resolve("velocity.toml"), config);
        Files.writeString(dir.resolve("forwarding.secret"), "unused\n");
        List<String> command = new ArrayList<>();
        command.add(context.config().javaExecutable(25).toString());
        command.add("-Xmx512M");
        command.add("-jar");
        command.add(jar.toString());
        return ServerProcess.start(command, dir, context.config().echo());
    }
}
