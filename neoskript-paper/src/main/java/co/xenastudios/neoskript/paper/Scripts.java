/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.ScriptCatalog;
import co.xenastudios.neoskript.runtime.ScriptRef;
import co.xenastudios.neoskript.runtime.Values;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Scripts as values: the scripts with a name or in a folder, all of them, and whether they're loaded. */
final class Scripts {

    private Scripts() {}

    static void register(Registrar registrar) {
        registrar.expression("ExprScriptNamed", "script", info -> {
            boolean folder = info.pattern() == 1;
            return (context, args) -> {
                List<Object> scripts = new ArrayList<>();
                if (folder) {
                    String prefix = Values.text(args[0]);
                    String directory = prefix.endsWith("/") || prefix.endsWith(File.separator) ? prefix : prefix + "/";
                    for (ScriptRef script : ScriptCatalog.get().scripts(true, true)) {
                        if (script.name().startsWith(directory)) {
                            scripts.add(script);
                        }
                    }
                } else {
                    for (Object name : (Object[]) args[0]) {
                        ScriptRef script = ScriptCatalog.get().find(Values.text(name));
                        if (script != null) {
                            scripts.add(script);
                        }
                    }
                }
                return scripts.size() == 1 && !folder ? scripts.getFirst() : scripts.toArray();
            };
        }, "[the] script[s] [named] %texts%", "[the] scripts in [directory|folder] %text%");
        registrar.expression("ExprScripts", "script", info -> {
            int pattern = info.pattern();
            return (context, args) -> ScriptCatalog.get().scripts(pattern != 2, pattern != 1).toArray();
        }, "[all [[of] the]|the] scripts", "[all [[of] the]|the] (enabled|loaded) scripts",
                "[all [[of] the]|the] (disabled|unloaded) scripts");
        registrar.condition("CondIsScriptLoaded", info -> {
            boolean negated = info.hasTag("not");
            return (context, args) -> {
                Object[] scripts = (Object[]) args[0];
                for (Object script : scripts) {
                    if (!(script instanceof ScriptRef ref) || ScriptCatalog.get().loaded(ref) == negated) {
                        return false;
                    }
                }
                return scripts.length > 0;
            };
        }, "%scripts% (is|are)[not:(n't| not)] loaded", "script[s] %scripts% (is|are)[not:(n't| not)] loaded");
    }

    /** Skript's test-only helpers. */
    static void registerTesting(Registrar registrar) {
        registrar.function("file_separator", List.<Parameter>of(), "string", false, args -> File.separator);
    }

    /** A script's name without {@code .sk}, as {@code the name of the script} is. */
    static String name(ScriptRef script) {
        String name = script.name();
        return name.endsWith(".sk") ? name.substring(0, name.length() - 3) : name;
    }
}
