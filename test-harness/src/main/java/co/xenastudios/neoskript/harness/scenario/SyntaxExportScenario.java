/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.frontend.registry.SyntaxExport;
import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerProcess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The data for neoskript.dev's syntax reference (website/scripts/syntax.mjs reads it). Boots NeoSkript with
 * {@code -Dneoskript.syntaxExport}, so it writes its registry, and the reference Skript, whose {@code sk gen-docs}
 * writes its documentation as JSON: names, descriptions, examples and since for each element. Both files go into
 * the reports folder as {@code syntax-export.json} and {@code skript-docs.json}. Run by name; no group selects it.
 */
public final class SyntaxExportScenario implements Scenario {

    private static final Pattern EXPORTED = Pattern.compile("Wrote the syntax export to ");
    private static final Pattern DOCS_DONE = Pattern.compile("JSON-only documentation generated!|Error while generating docs");

    @Override
    public String name() {
        return "syntax-export";
    }

    @Override
    public Group group() {
        return Group.MANUAL;
    }

    @Override
    public boolean supports(Environment environment) {
        return environment.skriptReference();
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        Path runs = context.config().runsDir().resolve(context.environment().name()).resolve(name());

        Path neoskriptRun = runs.resolve("neoskript");
        var neoskript = context.server(neoskriptRun).plugin(context.neoskriptJar());
        Path exportFile = neoskriptRun.resolve("syntax-export.json");
        neoskript.jvmArg("-Dneoskript.syntaxExport=" + exportFile.toAbsolutePath());
        try (ServerProcess server = neoskript.start()) {
            server.await(EXPORTED, Duration.ofMinutes(5));
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            server.stop();
        }
        if (!Files.exists(exportFile)) {
            throw new HarnessFailure("NeoSkript didn't write " + exportFile);
        }
        SyntaxExport export = SyntaxExport.fromJson(Files.readString(exportFile));
        Path report = context.config().reportsDir().resolve(context.environment().name()).resolve("syntax-export.json");
        Files.createDirectories(report.getParent());
        Files.copy(exportFile, report, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Path skriptRun = runs.resolve("skript");
        // Skript only has gen-docs with a docs template folder or this property; without a template it writes
        // only docs.json.
        try (ServerProcess server = context.server(skriptRun).plugin(context.skriptJar())
                .jvmArg("-Dskript.testing.genDocs=true")
                .start()) {
            server.await(ServerProcess.DONE, Duration.ofMinutes(5));
            int mark = server.mark();
            server.command("sk gen-docs");
            server.await(DOCS_DONE, mark, Duration.ofMinutes(5));
            server.stop();
        }
        Path docs = skriptRun.resolve("plugins/Skript/docs/docs.json");
        if (!Files.exists(docs)) {
            throw new HarnessFailure("Skript didn't write " + docs);
        }
        Files.copy(docs, report.resolveSibling("skript-docs.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Map<String, Integer> counts = new TreeMap<>();
        export.elements().forEach(e -> counts.merge(e.kind(), 1, Integer::sum));
        counts.forEach(context::summary);
        context.summary("type", export.types().size());
        context.summary("function", export.functions().size());
    }
}
