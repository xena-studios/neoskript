/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.check;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The server NeoSkript's CLI starts to check a scripts folder: with {@code -Dneoskript.check=<scripts>} and
 * {@code -Dneoskript.check.report=<report.json>}, NeoSkript checks the scripts with the standard library the server
 * registered, writes the report, and stops the server. Nothing runs and no variables are read or written.
 */
public final class CheckMode {

    private static final String SCRIPTS = "neoskript.check";
    private static final String REPORT = "neoskript.check.report";

    private CheckMode() {}

    /** Whether the server was started to check scripts. */
    public static boolean requested() {
        return System.getProperty(SCRIPTS) != null && System.getProperty(REPORT) != null;
    }

    /** Checks the scripts and writes the report; a problem reading or writing goes in the report or is thrown. */
    public static void run(Library library) throws IOException {
        Path scripts = Path.of(System.getProperty(SCRIPTS));
        Path report = Path.of(System.getProperty(REPORT));
        List<ScriptCheck.ScriptReport> reports = new ScriptCheck(library).check(read(scripts));
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(json(reports)) + "\n");
    }

    /**
     * The scripts under {@code target}, named by their paths from it with '/', as NeoSkript names them. Files and
     * folders whose names start with '-' or '.' are skipped, as Skript skips them.
     */
    static List<SourceFile> read(Path target) throws IOException {
        List<SourceFile> files = new ArrayList<>();
        if (Files.isRegularFile(target)) {
            files.add(new SourceFile(target.getFileName().toString(), Files.readString(target, StandardCharsets.UTF_8)));
            return files;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = target.relativize(path);
                boolean disabled = false;
                for (Path part : relative) {
                    disabled |= part.toString().startsWith("-") || part.toString().startsWith(".");
                }
                if (!disabled && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sk")) {
                    files.add(new SourceFile(relative.toString().replace('\\', '/'),
                            Files.readString(path, StandardCharsets.UTF_8)));
                }
            }
        }
        return files;
    }

    private static Map<String, Object> json(List<ScriptCheck.ScriptReport> reports) {
        List<Map<String, Object>> scripts = new ArrayList<>();
        for (ScriptCheck.ScriptReport report : reports) {
            List<Map<String, Object>> findings = new ArrayList<>();
            for (ScriptCheck.Finding finding : report.findings()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("kind", finding.kind().name().toLowerCase(Locale.ROOT));
                entry.put("line", finding.line());
                entry.put("message", finding.message());
                findings.add(entry);
            }
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("name", report.name());
            script.put("loads", report.loads());
            script.put("findings", findings);
            scripts.add(script);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("scripts", scripts);
        return json;
    }
}
