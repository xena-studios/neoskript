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
    /** What each placeholder of every registered pattern accepts, by placeholder identity. Read-only. */
    private final Map<PlaceholderSpec, Expected> byPlaceholder = new IdentityHashMap<>();
    private final Map<Expected, Expected> interned = new ConcurrentHashMap<>();

    ParserContext(SyntaxRegistry registry, Map<String, List<FunctionInfo>> scriptFunctions) {
        this.registry = registry;
        this.scriptFunctions = Map.copyOf(scriptFunctions);
        for (SyntaxKind kind : SyntaxKind.values()) {
            for (SyntaxInfo syntax : registry.syntaxes(kind)) {
                for (CompiledPattern pattern : syntax.patterns()) {
                    collect(pattern.root());
                }
            }
        }
    }

    private void collect(PatternElement element) {
        switch (element) {
            case PatternElement.Placeholder placeholder ->
                    byPlaceholder.put(placeholder.spec(), intern(resolve(placeholder.spec())));
            case PatternElement.Sequence sequence -> sequence.elements().forEach(this::collect);
            case PatternElement.Choice choice -> choice.alternatives().forEach(a -> collect(a.body()));
            case PatternElement.Optional optional -> collect(optional.choice());
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
        List<FunctionInfo> functions = scriptFunctions.get(name);
        if (functions != null) {
            return functions;
        }
        return registry.functions(name);
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
