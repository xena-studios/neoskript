/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** What a scenario gets to work with for one environment. */
public final class ScenarioContext {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final HarnessConfig config;
    private final Environment environment;
    private final Scenario scenario;
    private final Downloads downloads;
    private final ReferenceSkript referenceSkript;
    private final Map<String, Object> summary = new LinkedHashMap<>();

    ScenarioContext(
            HarnessConfig config,
            Environment environment,
            Scenario scenario,
            Downloads downloads,
            ReferenceSkript referenceSkript) {
        this.config = config;
        this.environment = environment;
        this.scenario = scenario;
        this.downloads = downloads;
        this.referenceSkript = referenceSkript;
    }

    public Environment environment() {
        return environment;
    }

    public HarnessConfig config() {
        return config;
    }

    public ReferenceSkript referenceSkript() {
        return referenceSkript;
    }

    public Path neoskriptJar() {
        return config.neoskriptJar();
    }

    public Path skriptJar() throws IOException, InterruptedException {
        return downloads.fetch(
                referenceSkript.jarUrl(), referenceSkript.jarSha256(), "Skript-" + referenceSkript.version() + ".jar");
    }

    /** Reads a script from {@code test-harness/scripts}. */
    public String script(String name) throws IOException {
        return Files.readString(config.scriptsDir().resolve(name));
    }

    /** A builder for a server in a fresh, empty directory for this scenario. */
    public ServerBuilder server() throws IOException, InterruptedException {
        return server(config.runsDir().resolve(environment.name()).resolve(scenario.name()));
    }

    /** A builder for a server in {@code runDir}, which is emptied first. */
    public ServerBuilder server(Path runDir) throws IOException, InterruptedException {
        Environment.Download download = environment.download();
        Path serverJar = downloads.fetch(download.url(), download.sha256(), environment.name() + ".jar");
        deleteRecursively(runDir);
        Files.createDirectories(runDir);
        return new ServerBuilder(config, environment, serverJar, runDir);
    }

    /** Adds a value to this run's line in the summary and report. */
    public void summary(String key, Object value) {
        summary.put(key, value);
    }

    Map<String, Object> summary() {
        return summary;
    }

    /** Writes a JSON report to {@code build/harness/reports/<environment>/<name>.json}. */
    public Path writeReport(String name, Object report) throws IOException {
        Path file = config.reportsDir().resolve(environment.name()).resolve(name + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(report) + "\n");
        return file;
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
