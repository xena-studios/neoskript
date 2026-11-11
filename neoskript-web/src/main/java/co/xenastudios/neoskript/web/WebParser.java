/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.web;

import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.structure.Line;
import java.util.List;
import java.util.Locale;

/**
 * Parses one script with the frontend and reports what the page shows, as JSON: its problems, and an
 * outline of its structures.
 *
 * <pre>{@code
 * {"diagnostics": [{"severity": "error", "line": 3, "column": 4, "length": 5, "message": "..."}],
 *  "outline": [{"kind": "event", "line": 1, "text": "on join"}]}
 * }</pre>
 *
 * Lines are 1-based, columns 0-based; a length of 0 means the whole line.
 */
public final class WebParser {

    /** What the script's problems are reported under. */
    static final String FILE_NAME = "script.sk";

    private final ScriptLoader loader;

    public WebParser(SyntaxRegistry registry) {
        this.loader = new ScriptLoader(registry);
    }

    public String parse(String source) {
        ParsedScript script = loader.load(List.of(new SourceFile(FILE_NAME, source))).getFirst();
        StringBuilder out = new StringBuilder("{\"diagnostics\":[");
        boolean first = true;
        for (Diagnostic diagnostic : script.diagnostics()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("{\"severity\":").append(Json.quote(diagnostic.severity().name().toLowerCase(Locale.ROOT)))
                    .append(",\"line\":").append(diagnostic.line())
                    .append(",\"column\":").append(diagnostic.column())
                    .append(",\"length\":").append(diagnostic.length())
                    .append(",\"message\":").append(Json.quote(diagnostic.message()))
                    .append('}');
        }
        out.append("],\"outline\":[");
        first = true;
        for (ParsedStructure structure : script.structures()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            Line line = structure.structure().node().line();
            out.append("{\"kind\":").append(Json.quote(kind(structure)))
                    .append(",\"line\":").append(line.number())
                    .append(",\"text\":").append(Json.quote(line.text()))
                    .append('}');
        }
        return out.append("]}").toString();
    }

    private static String kind(ParsedStructure structure) {
        return switch (structure) {
            case ParsedStructure.Event event -> "event";
            case ParsedStructure.Command command -> "command";
            case ParsedStructure.Function function -> "function";
            case ParsedStructure.Custom custom -> "structure";
            case ParsedStructure.Parse parse -> "parse";
            case ParsedStructure.AutoReload autoReload -> "auto reload";
            case ParsedStructure.Data data -> data.structure().kind().name().toLowerCase(Locale.ROOT);
        };
    }
}
