/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerBuilder;
import co.xenastudios.neoskript.harness.ServerProcess;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The official addons in {@code addons/}, as released: each jar loads next to NeoSkript and registers its syntax, and
 * a script uses the template's. With the community addon the template stands in for in {@code plugins/} (a jar that
 * needs Skript, so Paper can't load it), the template still loads, and the console says to remove that jar.
 */
public final class NeoSkriptOfficialAddonsScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(5);
    /** The community addon the template says it replaces, in its build file. */
    private static final String ORIGINAL = "skript-template";

    @Override
    public String name() {
        return "neoskript-official-addons";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        return true;
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        List<Path> jars = context.officialAddonJars();
        if (jars.isEmpty()) {
            throw new HarnessFailure("No official addon jars were given to the harness");
        }
        ServerBuilder builder = context.server().plugin(context.neoskriptJar());
        for (Path jar : jars) {
            builder.plugin(jar);
        }
        Path original = builder.runDir().resolveSibling(name() + "-original").resolve(ORIGINAL + ".jar");
        writeOriginal(original);
        builder.plugin(original).file("plugins/NeoSkript/scripts/official.sk", """
                on load:
                \tsend "[officialtest] %the template greeting for "Steve"%" to console
                """);
        try (ServerProcess server = builder.start()) {
            // Paper refuses the community addon first, as it needs Skript; what's checked comes after that.
            Pattern refused = Pattern.compile(Pattern.quote("Could not load 'plugins/" + ORIGINAL + ".jar'") + "|"
                    + Pattern.quote("Could not load plugin '" + ORIGINAL));
            server.await(refused, WAIT);
            int after = server.lines(0).size();
            for (int i = 0; i < server.lines(0).size(); i++) {
                if (refused.matcher(server.lines(0).get(i)).find()) {
                    after = i + 1;
                    break;
                }
            }
            server.await(Pattern.compile(Pattern.quote("plugins/" + ORIGINAL + ".jar is " + ORIGINAL
                    + ", which needs Skript, so it doesn't load with NeoSkript. NeoSkriptTemplate replaces it")), after,
                    WAIT);
            for (Path jar : jars) {
                String addon = properties(jar).getProperty("name");
                server.await(Pattern.compile(Pattern.quote("Registered the syntax of " + addon + " ")), after, WAIT);
            }
            server.await(Pattern.compile(Pattern.quote("[officialtest] Hello, Steve!") + "$"), after, WAIT);
            server.await(ServerProcess.DONE, after, WAIT);
            server.stop();
            List<String> errors = server.linesMatching(NeoSkriptBootScenario.NEOSKRIPT_ERROR);
            if (!errors.isEmpty()) {
                throw new HarnessFailure("NeoSkript logged errors: " + errors + ". See " + server.logFile());
            }
        }
    }

    /** A stand-in for the community addon: a plugin that needs Skript, which Paper won't load without it. */
    private static void writeOriginal(Path jar) throws IOException {
        Files.createDirectories(jar.getParent());
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write(("name: " + ORIGINAL + "\nversion: 1.0\nmain: example.original.Original\napi-version: '1.21'\n"
                    + "folia-supported: true\ndepend: [Skript]\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    /** What an official addon's build says about it, in the jar's {@code neoskript-addon.properties}. */
    static Properties properties(Path jar) throws IOException {
        Properties properties = new Properties();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("neoskript-addon.properties");
            if (entry == null) {
                throw new HarnessFailure(jar + " has no neoskript-addon.properties: is it built with the addon"
                        + " conventions?");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                properties.load(in);
            }
        }
        return properties;
    }
}
