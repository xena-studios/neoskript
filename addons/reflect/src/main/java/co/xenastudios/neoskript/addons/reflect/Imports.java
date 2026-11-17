/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import co.xenastudios.neoskript.api.registration.ScriptStructure;
import co.xenastudios.neoskript.api.registration.StructureHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * skript-reflect's {@code import:} blocks: each script's own names for Java classes, as {@code System} for
 * {@code java.lang.System}, with the same messages for a bad line. Forgotten at the start of each load, as every
 * script is parsed again then, so no class is held on to for a script that's gone.
 */
final class Imports implements StructureHandler {

    static final String IDENTIFIER = "[_a-zA-Z$][\\w$]*";
    static final String PACKAGE = "(?:" + IDENTIFIER + "\\.)*(?:" + IDENTIFIER + ")";
    private static final Pattern STATEMENT = Pattern.compile("(" + PACKAGE + ")(?:\\s+as (" + IDENTIFIER + "))?");

    /**
     * Each script's names, replaced whole at the start of each load. A call keeps its own script's map from when it
     * was set up ({@link #of}), so triggers still running from before a reload keep theirs.
     */
    private volatile Map<String, Map<String, JavaType>> byScript = new ConcurrentHashMap<>();
    private final ClassLoader loader;

    Imports(ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public void reset() {
        byScript = new ConcurrentHashMap<>();
    }

    @Override
    public void load(ScriptStructure structure) {
        Map<String, JavaType> imports = byScript.computeIfAbsent(structure.script(), s -> new ConcurrentHashMap<>());
        for (ScriptStructure.Entry entry : structure.entries()) {
            String text = entry.text().strip();
            Matcher statement = STATEMENT.matcher(text);
            if (!statement.matches()) {
                structure.error(entry.line(), text + " is an invalid import statement.");
                continue;
            }
            String name = statement.group(1);
            Class<?> javaClass = load(name);
            if (javaClass == null) {
                structure.error(entry.line(), name + " refers to a non-existent class.");
                continue;
            }
            String alias = statement.group(2);
            if (javaClass.getSimpleName().equals(alias)) {
                structure.warning(entry.line(), name + " doesn't need the alias " + alias
                        + ", as it will already be imported under that name");
            }
            String as = alias == null ? javaClass.getSimpleName() : alias;
            JavaType old = imports.putIfAbsent(as, new JavaType(javaClass));
            if (old != null) {
                structure.error(entry.line(), as + " is already mapped to " + old.javaClass() + ". It will not be "
                        + "remapped to " + javaClass + ".");
            }
        }
    }

    /**
     * {@code script}'s imports, by name: complete once the script's triggers are being parsed, as structures are
     * handled first. Empty for a script that imports nothing.
     */
    Map<String, JavaType> of(String script) {
        Map<String, JavaType> imports = script == null ? null : byScript.get(script);
        return imports == null ? Map.of() : imports;
    }

    /** What {@code name} is imported as in {@code script}, or {@code null}. */
    JavaType lookup(String script, String name) {
        if (script == null) {
            return null;
        }
        Map<String, JavaType> imports = byScript.get(script);
        return imports == null ? null : imports.get(name);
    }

    /** A class by its full name, as Java writes it ({@code org.bukkit.entity.EnderDragon$Phase}), or {@code null}. */
    Class<?> load(String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
