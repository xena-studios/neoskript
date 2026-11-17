/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.cli;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * {@code neoskript-cli check <scripts folder>}: what NeoSkript makes of a server's scripts before it's installed.
 *
 * <p>Item, block and entity names come from the server's registries, which only a server has, so the check starts
 * a throwaway headless Paper server (downloaded once and checked against its SHA-256) with NeoSkript in its check
 * mode: it checks the scripts with the real standard library, without running them, writes a report and stops.
 * Exits with 0 when every script loads, 1 when some don't, and 2 when the check couldn't run.
 */
public final class NeoSkriptCli {

    private static final String USAGE = """
            Usage: neoskript-cli check <scripts folder or .sk file> [options]
                   neoskript-cli addon <addon jar> [--members] [--json <file>]
                   neoskript-cli --version

              Checks the scripts as NeoSkript loads them, without running them, and reports:
                error        lines NeoSkript won't load (Skript's error, or syntax NeoSkript doesn't have)
                warning      Skript's warnings
                folia        what may behave differently on Folia
                performance  what's likely to cost a lot of server time

              Options:
                --json <file>        also write the report as JSON
                --hints-only         only show Folia and performance hints
                --quiet              only print the summary
                --minecraft <26.3|1.21.4>
                                     the server version to check against (by default the newest your Java runs)
                --aliases <folder>   alias files to read, as Skript reads plugins/Skript/aliases (by default the
                                     aliases folder next to the scripts folder, if there is one)
                --cache <folder>     where the server is downloaded to (default ~/.neoskript/cache)
                --verbose            show the server's console

              'addon' lists what a Skript addon's jar uses of Skript's classes (ch.njol.skript and
              org.skriptlang.skript), most used first: what it extends, calls and reads, and with --members,
              each method and field. It reads the jar's bytecode; nothing in it runs.

              Files and folders whose names start with '-' or '.' are skipped, as Skript skips them.
              Skript's default aliases are read unless the config.sk next to the aliases folder says
              'load default aliases: false'.
            """;

    /** The servers the check can run on, newest first: Minecraft version, the Java version it needs, its pin. */
    private record Server(String minecraft, int java, String url, String sha256) {}

    private NeoSkriptCli() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && (args[0].equals("--version") || args[0].equals("version"))) {
            out.println("neoskript-cli " + version());
            return 0;
        }
        if (args.length >= 2 && args[0].equals("addon")) {
            return addon(args, out, err);
        }
        if (args.length < 2 || !args[0].equals("check")) {
            err.print(USAGE);
            return 2;
        }
        Path target = Path.of(args[1]).toAbsolutePath().normalize();
        Path json = null;
        Path cache = Path.of(System.getProperty("user.home"), ".neoskript", "cache");
        String minecraft = null;
        boolean quiet = false;
        boolean hintsOnly = false;
        boolean verbose = false;
        Path aliases = null;
        for (int i = 2; i < args.length; i++) {
            String option = args[i];
            boolean takesValue = option.equals("--json") || option.equals("--minecraft") || option.equals("--cache")
                    || option.equals("--aliases");
            if (takesValue && i + 1 == args.length) {
                err.print(USAGE);
                return 2;
            }
            switch (option) {
                case "--json" -> json = Path.of(args[++i]);
                case "--minecraft" -> minecraft = args[++i];
                case "--cache" -> cache = Path.of(args[++i]);
                case "--aliases" -> aliases = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--quiet" -> quiet = true;
                case "--hints-only" -> hintsOnly = true;
                case "--verbose" -> verbose = true;
                default -> {
                    err.println("Unknown option: " + option);
                    err.print(USAGE);
                    return 2;
                }
            }
        }
        if (!Files.exists(target)) {
            err.println("No such file or folder: " + target);
            return 2;
        }
        if (aliases != null && !Files.isDirectory(aliases)) {
            err.println("No such folder: " + aliases);
            return 2;
        }
        if (aliases == null) {
            aliases = aliasesNextTo(target);
        }
        boolean defaultAliases = aliases == null || defaultAliases(aliases.resolveSibling("config.sk"));

        JsonObject report;
        try {
            Server server = server(minecraft, Runtime.version().feature());
            if (server == null) {
                err.println(minecraft == null
                        ? "The check needs Java 21 or newer (Java 25 for Minecraft 26.3); this is Java "
                                + Runtime.version().feature() + "."
                        : "Can't check against Minecraft " + minecraft + " with Java " + Runtime.version().feature()
                                + ". Minecraft 26.3 needs Java 25, and 1.21.4 Java 21.");
                return 2;
            }
            err.println("Checking against NeoSkript on Paper " + server.minecraft() + "...");
            if (aliases != null) {
                err.println("Reading the alias files in " + aliases + (defaultAliases ? "" : ", without Skript's defaults"));
            }
            report = check(target, aliases, defaultAliases, server, cache, verbose ? err : null);
        } catch (IOException | InterruptedException e) {
            err.println("Couldn't run the check: " + e.getMessage());
            return 2;
        }
        if (report == null) {
            err.println("The check server stopped without a report. Run again with --verbose to see its console.");
            return 2;
        }
        print(target, report, out, quiet, hintsOnly);
        if (json != null) {
            try {
                Files.writeString(json, new Gson().toJson(report) + "\n");
            } catch (IOException e) {
                err.println("Couldn't write " + json + ": " + e.getMessage());
                return 2;
            }
        }
        for (JsonElement script : report.getAsJsonArray("scripts")) {
            if (!script.getAsJsonObject().get("loads").getAsBoolean()) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * The aliases folder that goes with {@code target}, as Skript's {@code plugins/Skript/aliases} goes with
     * {@code plugins/Skript/scripts}: beside the {@code scripts} folder the target is in or is (so a script in
     * {@code scripts/games/} finds it too), else beside the target's folder. {@code null} if there isn't one.
     */
    static Path aliasesNextTo(Path target) {
        Path folder = Files.isDirectory(target) ? target : target.getParent();
        for (Path each = folder; each != null; each = each.getParent()) {
            if (each.getFileName() != null && each.getFileName().toString().equals("scripts") && each.getParent() != null
                    && Files.isDirectory(each.getParent().resolve("aliases"))) {
                return each.getParent().resolve("aliases");
            }
        }
        Path parent = folder == null ? null : folder.getParent();
        Path aliases = parent == null ? null : parent.resolve("aliases");
        return aliases != null && Files.isDirectory(aliases) ? aliases : null;
    }

    /** Skript's {@code load default aliases} from a config.sk, true if it doesn't say. */
    static boolean defaultAliases(Path configSk) {
        if (!Files.isRegularFile(configSk)) {
            return true;
        }
        try {
            for (String line : Files.readAllLines(configSk, StandardCharsets.UTF_8)) {
                String option = line.strip();
                if (option.startsWith("load default aliases:")) {
                    String value = option.substring(option.indexOf(':') + 1);
                    int comment = value.indexOf('#');
                    value = (comment >= 0 ? value.substring(0, comment) : value).strip().toLowerCase(Locale.ROOT);
                    return !(value.equals("false") || value.equals("no") || value.equals("off"));
                }
            }
        } catch (IOException | RuntimeException e) {
            return true;
        }
        return true;
    }

    /** This CLI's version, from its jar's manifest ({@code dev} when run from the build's classes). */
    static String version() {
        String version = NeoSkriptCli.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    /** The newest server that Java {@code java} runs, or the one asked for if it runs it. */
    private static Server server(String minecraft, int java) throws IOException {
        for (Server server : servers()) {
            if ((minecraft == null || server.minecraft().equals(minecraft)) && java >= server.java()) {
                return server;
            }
        }
        return null;
    }

    private static List<Server> servers() throws IOException {
        List<Server> servers = new ArrayList<>();
        for (String name : List.of("paper-26.3.json", "paper-1.21.4.json")) {
            try (InputStream in = NeoSkriptCli.class.getResourceAsStream("/servers/" + name)) {
                if (in == null) {
                    throw new IOException("the CLI is missing its server pin " + name);
                }
                JsonObject pin = new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
                JsonObject download = pin.getAsJsonObject("download");
                servers.add(new Server(pin.get("minecraftVersion").getAsString(), pin.get("javaVersion").getAsInt(),
                        download.get("url").getAsString(), download.get("sha256").getAsString()));
            }
        }
        return servers;
    }

    /** Runs the check on a server in a folder of its own, which is deleted after. */
    private static JsonObject check(Path scripts, Path aliases, boolean defaultAliases, Server server, Path cache,
            PrintStream console) throws IOException, InterruptedException {
        Path serverJar = download(server, cache);
        Path dir = Files.createTempDirectory("neoskript-check");
        try {
            Files.writeString(dir.resolve("eula.txt"), "eula=true\n");
            Files.writeString(dir.resolve("server.properties"), """
                    online-mode=false
                    server-port=%d
                    level-type=minecraft\\:flat
                    generate-structures=false
                    spawn-monsters=false
                    spawn-npcs=false
                    view-distance=2
                    simulation-distance=2
                    """.formatted(freePort()));
            Files.createDirectories(dir.resolve("plugins/bStats"));
            Files.writeString(dir.resolve("plugins/bStats/config.yml"), "enabled: false\n");
            try (InputStream plugin = NeoSkriptCli.class.getResourceAsStream("/neoskript-plugin.jar")) {
                if (plugin == null) {
                    throw new IOException("the CLI is missing NeoSkript's plugin");
                }
                Files.copy(plugin, dir.resolve("plugins/NeoSkript.jar"));
            }
            Path report = dir.resolve("report.json");
            String java = ProcessHandle.current().info().command().orElse("java");
            List<String> command = new ArrayList<>(List.of(java, "-Xmx2G", "-Ddisable.watchdog=true",
                    "-DbundlerRepoDir=" + cache.resolve("bundler").resolve(server.minecraft()),
                    "-Dneoskript.check=" + scripts, "-Dneoskript.check.report=" + report,
                    "-Dneoskript.check.defaultAliases=" + defaultAliases));
            if (aliases != null) {
                command.add("-Dneoskript.check.aliases=" + aliases);
                // The plugin's folder, where aliases-english.zip replaces Skript's default aliases if it's there.
                if (aliases.getParent() != null) {
                    command.add("-Dneoskript.check.dataFolder=" + aliases.getParent());
                }
            }
            command.addAll(List.of("-jar", serverJar.toString(), "--nogui"));
            Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
            process.getOutputStream().close();
            try (InputStream output = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                for (int read; (read = output.read(buffer)) >= 0; ) {
                    if (console != null) {
                        console.write(buffer, 0, read);
                    }
                }
            }
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return null;
            }
            return Files.exists(report)
                    ? new Gson().fromJson(Files.readString(report, StandardCharsets.UTF_8), JsonObject.class)
                    : null;
        } finally {
            deleteRecursively(dir);
        }
    }

    /** The server's jar from the cache, downloaded first if needed, and checked against its pinned SHA-256. */
    private static Path download(Server server, Path cache) throws IOException, InterruptedException {
        Path jar = cache.resolve("paper-" + server.minecraft() + "-" + server.sha256().substring(0, 12) + ".jar");
        if (Files.exists(jar) && sha256(jar).equals(server.sha256())) {
            return jar;
        }
        Files.createDirectories(cache);
        Path partial = Files.createTempFile(cache, "download", ".part");
        try (InputStream in = URI.create(server.url()).toURL().openStream()) {
            Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!sha256(partial).equals(server.sha256())) {
            Files.deleteIfExists(partial);
            throw new IOException("the downloaded Paper " + server.minecraft() + " doesn't match its pinned SHA-256");
        }
        Files.move(partial, jar, StandardCopyOption.REPLACE_EXISTING);
        return jar;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                for (int read; (read = in.read(buffer)) >= 0; ) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code addon <jar> [--members] [--json <file>]}: what an addon's jar uses of Skript. */
    private static int addon(String[] args, PrintStream out, PrintStream err) {
        Path jar = Path.of(args[1]);
        boolean members = false;
        Path json = null;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--members" -> members = true;
                case "--json" -> {
                    if (i + 1 == args.length) {
                        err.print(USAGE);
                        return 2;
                    }
                    json = Path.of(args[++i]);
                }
                default -> {
                    err.print(USAGE);
                    return 2;
                }
            }
        }
        try {
            AddonScan.Result result = AddonScan.scan(jar);
            out.print(AddonScan.report(result, members));
            if (json != null) {
                java.nio.file.Files.writeString(json, new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                        .toJson(AddonScan.json(result)) + "\n");
            }
            return 0;
        } catch (IOException e) {
            err.println("Couldn't read " + jar + ": " + e.getMessage());
            return 2;
        }
    }

    private static int freePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static int countFiles(JsonArray problems) {
        java.util.Set<String> files = new java.util.HashSet<>();
        for (JsonElement problem : problems) {
            files.add(problem.getAsJsonObject().get("file").getAsString());
        }
        return files.size();
    }

    static void print(Path target, JsonObject report, PrintStream out, boolean quiet, boolean hintsOnly) {
        JsonArray scripts = report.getAsJsonArray("scripts");
        int loading = 0;
        int hints = 0;
        for (JsonElement element : scripts) {
            JsonObject script = element.getAsJsonObject();
            loading += script.get("loads").getAsBoolean() ? 1 : 0;
            for (JsonElement finding : script.getAsJsonArray("findings")) {
                String kind = finding.getAsJsonObject().get("kind").getAsString();
                hints += kind.equals("folia") || kind.equals("performance") ? 1 : 0;
            }
        }
        out.printf("NeoSkript check of %s: %d script%s, %d load%s cleanly, %d with errors, %d hint%s%n", target,
                scripts.size(), scripts.size() == 1 ? "" : "s", loading, loading == 1 ? "s" : "",
                scripts.size() - loading, hints, hints == 1 ? "" : "s");
        JsonObject aliases = report.has("aliases") ? report.getAsJsonObject("aliases") : null;
        JsonArray aliasProblems = aliases == null ? new JsonArray() : aliases.getAsJsonArray("problems");
        if (aliases != null && aliases.get("files").getAsInt() > 0) {
            int errors = 0;
            for (JsonElement problem : aliasProblems) {
                errors += problem.getAsJsonObject().get("kind").getAsString().equals("error") ? 1 : 0;
            }
            errors += aliases.has("unlistedErrors") ? aliases.get("unlistedErrors").getAsInt() : 0;
            out.printf("Alias files: %d read, %d with problems (%d error%s)%n", aliases.get("files").getAsInt(),
                    countFiles(aliasProblems), errors, errors == 1 ? "" : "s");
        }
        if (quiet) {
            return;
        }
        if (!hintsOnly && !aliasProblems.isEmpty()) {
            String file = null;
            for (JsonElement element : aliasProblems) {
                JsonObject problem = element.getAsJsonObject();
                if (!problem.get("file").getAsString().equals(file)) {
                    file = problem.get("file").getAsString();
                    out.println();
                    out.println("aliases/" + file);
                }
                int line = problem.get("line").getAsInt();
                out.printf(Locale.ROOT, "  %-11s %s%s%n", problem.get("kind").getAsString(),
                        line > 0 ? "line " + line + ": " : "", problem.get("message").getAsString());
            }
            int unlisted = (aliases.has("unlistedErrors") ? aliases.get("unlistedErrors").getAsInt() : 0)
                    + (aliases.has("unlistedWarnings") ? aliases.get("unlistedWarnings").getAsInt() : 0);
            if (unlisted > 0) {
                out.printf(Locale.ROOT, "  ... and %d more %s in the alias files, not listed%n", unlisted,
                        unlisted == 1 ? "problem" : "problems");
            }
        }
        for (JsonElement element : scripts) {
            JsonObject script = element.getAsJsonObject();
            List<String> lines = new ArrayList<>();
            for (JsonElement entry : script.getAsJsonArray("findings")) {
                JsonObject finding = entry.getAsJsonObject();
                String kind = finding.get("kind").getAsString();
                if (hintsOnly && !kind.equals("folia") && !kind.equals("performance")) {
                    continue;
                }
                lines.add(String.format(Locale.ROOT, "  %-11s line %d: %s", kind, finding.get("line").getAsInt(),
                        finding.get("message").getAsString()));
            }
            JsonArray extensions = script.has("extensions") ? script.getAsJsonArray("extensions") : new JsonArray();
            if (!extensions.isEmpty() && !hintsOnly) {
                List<String> names = new ArrayList<>();
                extensions.forEach(name -> names.add(name.getAsString()));
                lines.addFirst(String.format(Locale.ROOT, "  %-11s uses NeoSkript's language %s %s, so it won't run on"
                        + " Skript", "extensions", names.size() == 1 ? "extension" : "extensions", String.join(", ", names)));
            }
            if (!lines.isEmpty()) {
                out.println();
                out.println(script.get("name").getAsString());
                lines.forEach(out::println);
            }
        }
    }
}
