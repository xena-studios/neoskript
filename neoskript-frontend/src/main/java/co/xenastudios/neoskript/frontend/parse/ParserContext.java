/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
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
    private final Map<String, List<FunctionInfo>> scriptFunctions;
    /** The current script's local functions, which take precedence over global ones with the same name. */
    private final Map<String, List<FunctionInfo>> localFunctions;
    /** Per registry: what each placeholder of every registered pattern accepts, built once. */
    private static final Map<SyntaxRegistry, Map<PlaceholderSpec, Expected>> PLACEHOLDERS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

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
        this.scriptFunctions = Map.copyOf(scriptFunctions);
        this.localFunctions = Map.copyOf(localFunctions);
        this.byPlaceholder = PLACEHOLDERS.computeIfAbsent(registry, this::placeholders);
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
        itemAliases = java.util.Set.copyOf(names);
    }

    /** Whether {@code words} name one of the script's item aliases, with an amount or article before it or not. */
    boolean isItemAlias(String words) {
        if (itemAliases.isEmpty()) {
            return false;
        }
        String name = words.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ")
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
