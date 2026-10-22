/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.structure;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Reads a script into its top-level structures, with comments removed and options substituted. */
public final class ScriptParser {

    private ScriptParser() {}

    public record Result(SourceFile file, List<Structure> structures, Map<String, String> options,
            List<Diagnostic> diagnostics) {}

    public static Result parse(SourceFile file) {
        Diagnostics diagnostics = new Diagnostics(file);
        List<Node> nodes = StructureParser.parse(file, diagnostics);
        Map<String, String> options = Options.collect(nodes, diagnostics);
        nodes = Options.apply(nodes, options, diagnostics);

        List<Structure> structures = new ArrayList<>();
        for (Node node : nodes) {
            switch (node) {
                case Node.Section section -> structures.add(new Structure(Structure.kindOf(section.line()), section));
                case Node.Simple simple -> {
                    Line line = simple.line();
                    Structure.Kind kind = Structure.simpleKindOf(line);
                    if (kind != null) {
                        structures.add(new Structure(kind, simple));
                        continue;
                    }
                    diagnostics.error(line.number(), line.column(), line.text().length(),
                            "Can't understand this line: code has to go inside an event, command, function or"
                                    + " other structure");
                }
            }
        }
        return new Result(file, List.copyOf(structures), Collections.unmodifiableMap(options), diagnostics.all());
    }
}
