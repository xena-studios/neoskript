/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The migration CLI, as a server owner runs it: {@code neoskript-cli check} on the real-world corpus loads every
 * script cleanly, and on a script with known hazards gives the Folia and performance hints for them. The CLI
 * starts its own check server, from the Paper build this environment pins.
 */
public final class NeoSkriptCliScenario implements Scenario {

    private static final String HAZARDS = """
            every 1 tick:
            \tloop all players:
            \t\tsend "hi" to loop-player
            \t\tset {_health} to health of loop-player
            every 30 seconds:
            \tset {counter} to {counter} + 1
            """;

    @Override
    public String name() {
        return "neoskript-cli";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        // The CLI checks on Paper; one version is enough to test it.
        return environment.name().equals("paper-26.3");
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path dir = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        Path scripts = dir.resolve("scripts");
        Path cache = dir.resolve("cache");
        Files.createDirectories(scripts);
        Files.createDirectories(cache);
        for (Path script : CorpusRunner.scripts(context.config().realWorldCorpus())) {
            Files.copy(script, scripts.resolve(script.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(scripts.resolve("corpus-assert.sk"), context.script("corpus-assert.sk"));
        Path hazards = dir.resolve("hazards");
        Files.createDirectories(hazards);
        Files.writeString(hazards.resolve("hazards.sk"), HAZARDS);
        // The pinned server, where the CLI looks for it, so it isn't downloaded again.
        Environment.Download download = context.environment().download();
        Path server = context.download(download.url(), download.sha256(), context.environment().name() + ".jar");
        Files.copy(server, cache.resolve("paper-" + context.environment().minecraftVersion() + "-"
                + download.sha256().substring(0, 12) + ".jar"), StandardCopyOption.REPLACE_EXISTING);

        JsonObject corpus = check(context, scripts, cache, dir.resolve("corpus.json"), 0);
        List<String> problems = new ArrayList<>();
        for (JsonElement script : corpus.getAsJsonArray("scripts")) {
            if (!script.getAsJsonObject().get("loads").getAsBoolean()) {
                problems.add(script.getAsJsonObject().get("name").getAsString());
            }
        }
        if (!problems.isEmpty() || corpus.getAsJsonArray("scripts").size() != CorpusRunner.scripts(
                context.config().realWorldCorpus()).size() + 1) {
            throw new HarnessFailure("The CLI says these corpus scripts don't load: " + problems);
        }

        JsonObject hints = check(context, hazards, cache, dir.resolve("hazards.json"), 0);
        List<String> found = new ArrayList<>();
        for (JsonElement finding : hints.getAsJsonArray("scripts").get(0).getAsJsonObject().getAsJsonArray("findings")) {
            JsonObject entry = finding.getAsJsonObject();
            found.add(entry.get("kind").getAsString() + " " + entry.get("line").getAsInt());
        }
        List<String> expected = List.of("performance 1", "folia 4", "folia 6");
        if (!found.equals(expected)) {
            throw new HarnessFailure("The CLI's hints for the hazards script were " + found + ", expected " + expected);
        }
        context.summary("scripts", corpus.getAsJsonArray("scripts").size());
        context.summary("hints", found.size());
    }

    /** Runs {@code neoskript-cli check} and returns its JSON report, failing unless it exits with {@code exit}. */
    private static JsonObject check(ScenarioContext context, Path scripts, Path cache, Path json, int exit)
            throws Exception {
        Path java = context.config().javaExecutable(context.environment().javaVersion());
        Path log = json.resolveSibling(json.getFileName().toString().replace(".json", ".log"));
        Process process = new ProcessBuilder(java.toString(), "-jar", context.config().cliJar().toString(), "check",
                scripts.toString(), "--cache", cache.toString(), "--json", json.toString(), "--verbose")
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new HarnessFailure("The CLI didn't finish in 10 minutes. See " + log);
        }
        if (process.exitValue() != exit || !Files.exists(json)) {
            throw new HarnessFailure("The CLI exited with " + process.exitValue() + ", expected " + exit + ". See " + log);
        }
        return new Gson().fromJson(Files.readString(json, StandardCharsets.UTF_8), JsonObject.class);
    }
}
