/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.source.Text;
import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.Frozen;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import co.xenastudios.neoskript.frontend.pattern.PatternElement;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** What every line of a set of scripts is parsed against: the registry and the scripts' functions. */
final class ParserContext {

    private final SyntaxRegistry registry;

    /**
     * How long reading one statement or structure header may take. Real lines, even lists of a thousand values, take
     * well under a second; past this the parser's search grows much faster than the line (thousands of statements
     * run together, with quotes that pair up any number of ways), and the line is an error rather than minutes of
     * work on the thread that loads scripts.
     */
    static final long LINE_TIME_LIMIT_NANOS =
            Long.getLong("neoskript.parse.lineTimeLimitMillis", 10_000L) * 1_000_000L;

    /**
     * How long reading one script's lines may take in all, for each of its lines: a script of thousands of lines takes
     * well under a second, so this only stops a script made of many lines that each take nearly
     * {@link #LINE_TIME_LIMIT_NANOS}. It's never less than three lines' limit, and a bigger script gets more.
     */
    static final long SCRIPT_TIME_PER_LINE_NANOS = 20_000_000L;

    /** How many lines the script being read has. */
    private int scriptLines;

    /** How many lines are being read, one inside the other: a section's header and a line in it. */
    private int lineDepth;

    /** When the line being read runs out of time, in {@link System#nanoTime} terms. */
    private long deadline;

    /** When this script runs out of time, from when its first line started, once it has. */
    private long scriptDeadline;
    private boolean scriptStarted;

    /** The number of lines in the script, which gives it its time in all. */
    void scriptLines(int lines) {
        scriptLines = lines;
    }

    /** Starts the time for reading one line, returning what to give {@link #endLine} after it. */
    long startLine() {
        long outer = lineDepth;
        lineDepth++;
        deadline = lineDeadline();
        return outer;
    }

    /**
     * Ends a line {@link #startLine} started. The line around it, a section's header, gets its time again, as what's
     * in the section isn't its own.
     */
    void endLine(long outer) {
        lineDepth = (int) outer;
        if (lineDepth > 0) {
            deadline = lineDeadline();
        }
    }

    private long lineDeadline() {
        long now = System.nanoTime();
        if (!scriptStarted) {
            scriptStarted = true;
            scriptDeadline = now + Math.max(3 * LINE_TIME_LIMIT_NANOS, scriptLines * SCRIPT_TIME_PER_LINE_NANOS);
        }
        long line = now + LINE_TIME_LIMIT_NANOS;
        return line - scriptDeadline < 0 ? line : scriptDeadline;
    }

    /** Throws {@link LineParser.TooComplex} if the line being read has run out of time. */
    void checkTime() {
        if (lineDepth > 0 && System.nanoTime() - deadline > 0) {
            throw new LineParser.TooComplex();
        }
    }
    private final Map<String, List<FunctionInfo>> scriptFunctions;
    /** The current script's local functions, which take precedence over global ones with the same name. */
    private final Map<String, List<FunctionInfo>> localFunctions;
    /** Per registry: what each placeholder of every registered pattern accepts, built once. */
    private static final Map<SyntaxRegistry, Map<PlaceholderSpec, Expected>> PLACEHOLDERS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Per registry: each pattern's placeholders by index, worked out as they're asked for. */
    private static final Map<SyntaxRegistry, Map<CompiledPattern, PlaceholderSpec[]>> SPECS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private final Map<CompiledPattern, PlaceholderSpec[]> specs;

    /** What each placeholder of every registered pattern accepts, by placeholder identity. Read-only. */
    private final Map<PlaceholderSpec, Expected> byPlaceholder;
    private final Map<Expected, Expected> interned = new ConcurrentHashMap<>();
    /** The item aliases the script being parsed defines, which are items there. */
    private java.util.Set<String> itemAliases = java.util.Set.of();

    ParserContext(SyntaxRegistry registry, Map<String, List<FunctionInfo>> scriptFunctions) {
        this(registry, scriptFunctions, Map.of());
    }

    ParserContext(SyntaxRegistry registry, Map<String, List<FunctionInfo>> scriptFunctions,
            Map<String, List<FunctionInfo>> localFunctions) {
        this.registry = registry;
        this.scriptFunctions = Frozen.map(scriptFunctions);
        this.localFunctions = Frozen.map(localFunctions);
        this.byPlaceholder = PLACEHOLDERS.computeIfAbsent(registry, this::placeholders);
        this.specs = SPECS.computeIfAbsent(registry, r -> new ConcurrentHashMap<>());
        byPlaceholder.values().forEach(this::intern);
    }

    private Map<PlaceholderSpec, Expected> placeholders(SyntaxRegistry registry) {
        Map<PlaceholderSpec, Expected> result = new IdentityHashMap<>();
        for (SyntaxKind kind : SyntaxKind.values()) {
            for (SyntaxInfo syntax : registry.syntaxes(kind)) {
                for (CompiledPattern pattern : syntax.patterns()) {
                    collect(pattern.root(), result);
                }
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private void collect(PatternElement element, Map<PlaceholderSpec, Expected> result) {
        switch (element) {
            case PatternElement.Placeholder placeholder -> result.put(placeholder.spec(), resolve(placeholder.spec()));
            case PatternElement.Sequence sequence -> sequence.elements().forEach(e -> collect(e, result));
            case PatternElement.Choice choice -> choice.alternatives().forEach(a -> collect(a.body(), result));
            case PatternElement.Optional optional -> collect(optional.choice(), result);
            default -> {}
        }
    }

    /** A registered pattern's placeholders, by index ({@code null} where an index has none). Don't change it. */
    PlaceholderSpec[] specs(CompiledPattern pattern) {
        // (Patterns are compared by identity.)
        return specs.computeIfAbsent(pattern, p -> {
            PlaceholderSpec[] result = new PlaceholderSpec[p.placeholderCount()];
            collectSpecs(p.root(), result);
            return result;
        });
    }

    private static void collectSpecs(PatternElement element, PlaceholderSpec[] specs) {
        switch (element) {
            case PatternElement.Placeholder placeholder -> specs[placeholder.index()] = placeholder.spec();
            case PatternElement.Sequence sequence -> sequence.elements().forEach(e -> collectSpecs(e, specs));
            case PatternElement.Choice choice -> choice.alternatives().forEach(a -> collectSpecs(a.body(), specs));
            case PatternElement.Optional optional -> collectSpecs(optional.choice(), specs);
            default -> {}
        }
    }

    /** The shared instance equal to {@code expected}, so memo keys can compare by identity. */
    Expected intern(Expected expected) {
        Expected existing = interned.putIfAbsent(expected, expected);
        return existing != null ? existing : expected;
    }

    SyntaxRegistry registry() {
        return registry;
    }

    /** The overloads of a function: the scripts' own, or else the built-in one. Empty if there's none. */
    List<FunctionInfo> functions(String name) {
        List<FunctionInfo> local = localFunctions.get(name);
        if (local != null) {
            return local;
        }
        List<FunctionInfo> functions = scriptFunctions.get(name);
        if (functions != null) {
            return functions;
        }
        return registry.functions(name);
    }

    /** The script's local variable type hints, as its code is parsed in order. */
    private final TypeHints hints = new TypeHints();

    TypeHints hints() {
        return hints;
    }

    /** The event values the code being parsed can use, or {@code null} for any. */
    private co.xenastudios.neoskript.frontend.registry.EventValues.Scope eventScope;

    co.xenastudios.neoskript.frontend.registry.EventValues.Scope eventScope() {
        return eventScope;
    }

    /** Sets the event values the code parsed next can use ({@code null} for any), returning the ones before. */
    co.xenastudios.neoskript.frontend.registry.EventValues.Scope eventScope(
            co.xenastudios.neoskript.frontend.registry.EventValues.Scope scope) {
        co.xenastudios.neoskript.frontend.registry.EventValues.Scope before = eventScope;
        eventScope = scope;
        return before;
    }

    void itemAliases(java.util.Set<String> names) {
        itemAliases = Frozen.set(names);
    }

    /** The experiments the script being parsed is using, by code name. */
    private java.util.Set<String> experiments = java.util.Set.of();

    void experiments(java.util.Set<String> codeNames) {
        experiments = Frozen.set(codeNames);
    }

    /** Whether the script being parsed is using the experiment with this code name. */
    boolean using(String experiment) {
        return experiments.contains(experiment);
    }

    /** Whether the code may have waited before the line being parsed, for elements Skript refuses after a wait. */
    private co.xenastudios.neoskript.frontend.registry.DelayState delay =
            co.xenastudios.neoskript.frontend.registry.DelayState.NOT_DELAYED;

    void delay(co.xenastudios.neoskript.frontend.registry.DelayState delay) {
        this.delay = delay;
    }

    co.xenastudios.neoskript.frontend.registry.DelayState delay() {
        return delay;
    }

    /** The name of the script being parsed ({@code folder/script.sk}), for warnings that suggest it. */
    private String scriptName = "";

    void scriptName(String name) {
        scriptName = name;
    }

    String scriptName() {
        return scriptName;
    }

    /** Whether {@code words} name one of the script's item aliases, with an amount or article before it or not. */
    boolean isItemAlias(String words) {
        if (itemAliases.isEmpty()) {
            return false;
        }
        String name = Text.lower(words.strip()).replaceAll("\\s+", " ")
                .replaceFirst("^\\d+ (?:of )?", "").replaceFirst("^(?:an?|all|every|plain) ", "");
        int brackets = name.indexOf('[');
        return itemAliases.contains(brackets < 0 ? name : name.substring(0, brackets).strip());
    }

    /** What a placeholder accepts. Type names the registry doesn't know are treated as {@code object}. */
    Expected expected(PlaceholderSpec spec) {
        Expected expected = byPlaceholder.get(spec);
        return expected != null ? expected : intern(resolve(spec));
    }

    private Expected resolve(PlaceholderSpec s) {
        {
            List<TypeRef> refs = new ArrayList<>();
            boolean unknown = false;
            boolean plural = false;
            for (String name : s.types()) {
                TypeRef ref = registry.resolveType(name);
                if (ref == null) {
                    unknown = true;
                    plural |= name.strip().endsWith("s");
                } else {
                    refs.add(ref);
                    plural |= ref.plural();
                }
            }
            if (unknown) {
                return new Expected(List.of(), plural, s.literalOnly(), s.changeableOnly());
            }
            return Expected.of(refs, s.literalOnly(), s.changeableOnly());
        }
    }

    Expected expected(TypeRef ref) {
        return intern(Expected.of(List.of(ref), false, false));
    }
}
