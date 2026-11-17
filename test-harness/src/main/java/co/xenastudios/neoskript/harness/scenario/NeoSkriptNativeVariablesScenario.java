/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerBuilder;
import co.xenastudios.neoskript.harness.ServerProcess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * NeoSkript's native variable storage ({@code variables.storage: native}) on a real server, restarted in the same
 * directory each time:
 *
 * <ol>
 *   <li>In CSV storage, NeoSkript saves variables of many types to {@code variables.csv}.
 *   <li>Switched to native, it moves them to {@code variables.nsv} after a backup, reads them back, and changes a
 *       few, which the stop appends.
 *   <li>Restarted, it reads the changed values from {@code variables.nsv}, and with nothing changed, writes nothing.
 *   <li>Switched back to CSV, it writes {@code variables.csv} again with the same values, and the native file goes
 *       to {@code backups/}.
 * </ol>
 */
public final class NeoSkriptNativeVariablesScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(3);
    private static final String DATA = "plugins/NeoSkript/";
    private static final String NATIVE = "variables:\n  storage: native\n";
    private static final String CSV = "variables:\n  storage: csv\n";
    private static final String BEFORE = "from neoskript ✓ | 7 | 0.25 | false | 2 hours and 3 seconds | x, y and z | 1";
    private static final String AFTER = "from neoskript ✓ | 8 | 0.25 | <none> | 2 hours and 3 seconds | x, y, z and w | 1";
    private static final String ITEMS = "diamond sword named y | 5 stones | 2 diamonds | creative";

    @Override
    public String name() {
        return "neoskript-native-variables";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(co.xenastudios.neoskript.harness.Environment environment) {
        return true;
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());
        ServerBuilder server = context.server(runs)
                .plugin(context.neoskriptJar())
                .file(DATA + "config.yml", CSV)
                .file(DATA + "scripts/write.sk", context.script("native-variables-write.sk"));
        Path data = runs.resolve(DATA);

        // 1. CSV storage saves the variables.
        run(server, ServerProcess.DONE);
        expect(Files.exists(data.resolve("variables.csv")) && !Files.exists(data.resolve("variables.nsv")),
                "CSV storage saved variables.csv");

        // 2. Native storage takes them over, reads them, and changes a few.
        server.without(DATA + "scripts/write.sk")
                .file(DATA + "config.yml", NATIVE)
                .file(DATA + "scripts/change.sk", context.script("native-variables-change.sk"));
        List<String> imported = run(server, Pattern.compile("\\[harness] native changed"));
        expect(imported.stream().anyMatch(line -> line.contains("Moved ") && line.contains("from variables.csv to variables.nsv")),
                "NeoSkript logged moving variables.csv to variables.nsv");
        expect(!Files.exists(data.resolve("variables.csv")) && Files.exists(data.resolve("variables.nsv")),
                "variables.nsv replaced variables.csv");
        expect(backups(data, ".csv") == 1, "variables.csv was backed up before converting");
        line(imported, "native before: ", BEFORE);
        line(imported, "native items before: ", ITEMS + " | <none> | <none>");
        long saved = Files.size(data.resolve("variables.nsv"));

        // 3. Restarted, the changes are read back from the native file.
        server.without(DATA + "scripts/change.sk").file(DATA + "scripts/read.sk", context.script("native-variables-read.sk"));
        List<String> reloaded = run(server, Pattern.compile("\\[harness] native items: .*"));
        expect(Files.size(data.resolve("variables.nsv")) == saved, "a restart that changes nothing writes nothing");
        line(reloaded, "native read: ", AFTER);
        line(reloaded, "native items: ", ITEMS + " | x: 1, y: 2, z: 3, yaw: 0, pitch: 0 in 'world'");

        // 4. Back to CSV: variables.csv again, with the same values.
        server.file(DATA + "config.yml", CSV);
        List<String> exported = run(server, Pattern.compile("\\[harness] native items: .*"));
        expect(exported.stream().anyMatch(line -> line.contains("back to variables.csv")),
                "NeoSkript logged moving the variables back to variables.csv");
        expect(Files.exists(data.resolve("variables.csv")) && !Files.exists(data.resolve("variables.nsv")),
                "variables.csv replaced variables.nsv");
        expect(backups(data, ".nsv") == 1, "variables.nsv went to backups");
        line(exported, "native read: ", AFTER);
        line(exported, "native items: ", ITEMS + " | x: 1, y: 2, z: 3, yaw: 0, pitch: 0 in 'world'");
        context.summary("native bytes", saved);
    }

    /** Starts the server, waits for {@code marker}, stops it cleanly, and returns its console lines. */
    private static List<String> run(ServerBuilder builder, Pattern marker) throws Exception {
        try (ServerProcess server = builder.start()) {
            server.await(marker, WAIT);
            server.await(ServerProcess.DONE, WAIT);
            int exit = server.stop();
            if (exit != 0) {
                throw new HarnessFailure("Server exited with code " + exit + ". See " + server.logFile());
            }
            List<String> lines = server.linesMatching(Pattern.compile(".*"));
            if (lines.stream().anyMatch(line -> line.contains("Couldn't") && line.contains("variables"))) {
                throw new HarnessFailure("NeoSkript couldn't load or save variables. See " + server.logFile());
            }
            return lines;
        }
    }

    private static void line(List<String> lines, String marker, String expected) {
        String found = lines.stream().filter(line -> line.contains("[harness] " + marker)).findFirst().orElse("");
        if (!found.endsWith(expected)) {
            throw new HarnessFailure("NeoSkript printed '" + found + "', expected it to end with '" + expected + "'");
        }
    }

    private static long backups(Path data, String extension) throws IOException {
        Path dir = data.resolve("backups");
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(extension)).count();
        }
    }

    private static void expect(boolean condition, String what) {
        if (!condition) {
            throw new HarnessFailure("Expected: " + what);
        }
    }
}
