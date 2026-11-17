/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.KeyAware;
import co.xenastudios.neoskript.api.element.Keyed;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.element.Update;
import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Nested lists between scripts and addons: a {@link KeyAware} expression's map result sets nested indices, one with
 * {@link KeyAware#recursiveKeys()} is given a list variable's every value at its path, and an {@link Update} with a
 * map sets the indices it names in a list and leaves the rest.
 */
class KeyedResultsTest {

    private final List<String> said = Collections.synchronizedList(new ArrayList<>());
    private Library library;

    /** {@code {"name": "Steve", "home": {"x": 10, "y": 64}, "tags": ["a", "b"]}}. */
    private static Map<String, Object> tree() {
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("x", 10L);
        home.put("y", 64L);
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("name", "Steve");
        tree.put("home", home);
        tree.put("tags", List.of("a", "b"));
        return tree;
    }

    /** A key-aware expression giving {@link #tree()}. */
    private static final class TreeExpression implements Expression, KeyAware {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return tree();
        }

        @Override
        public boolean keysRecommended() {
            return true;
        }
    }

    /** 'paths of %objects%': each value with its path, from a list variable given recursively. */
    private static final class PathsExpression implements Expression, KeyAware {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (!(arguments[0] instanceof Keyed keyed)) {
                return "not keyed: " + Values.text(arguments[0]);
            }
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < keyed.values().length; i++) {
                parts.add(keyed.keys()[i] + "=" + Values.text(keyed.values()[i]));
            }
            return String.join(" ", parts);
        }

        @Override
        public boolean recursiveKeys() {
            return true;
        }
    }

    @BeforeEach
    void install() {
        library = new Library();
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        library.expression("TestExprTree", "object", info -> new TreeExpression(), "the tree");
        library.expression("TestExprPaths", "string", info -> new PathsExpression(), "paths of %objects%");
        library.effect("TestEffGraft", info -> Update.of(0, (context, args) -> tree()), "graft the tree onto %objects%");
    }

    private void run(String script) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        assertEquals(List.of(), errors(parsed.getFirst().diagnostics()), "parse problems");
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        compiled.linkFunctions();
        assertEquals(List.of(), errors(compiled.diagnostics()), "compile problems");
        compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(null)));
    }

    private static List<String> errors(List<Diagnostic> diagnostics) {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).map(Diagnostic::message).toList();
    }

    @Test
    void aMapResultSetsNestedIndices() {
        run("""
                on load:
                \tset {_t::*} to the tree
                \tsay "%{_t::name}% %{_t::home::x}% %{_t::home::y}% %{_t::tags::1}% %{_t::tags::2}%"
                \tsay "%size of {_t::*}%"
                \tsay "%the tree%"
                """);
        // Only Steve is directly in the list; the rest is in the lists under 'home' and 'tags'. Where indices
        // aren't wanted, the values are what's left.
        assertEquals(List.of("Steve 10 64 a b", "1", "Steve, 10, 64, a and b"), said);
    }

    @Test
    void aRecursiveArgumentHasEveryValueAtItsPath() {
        run("""
                on load:
                \tset {_t::name} to "Steve"
                \tset {_t::home::x} to 10
                \tset {_t::home} to "here"
                \tset {_t::tags::*} to "a" and "b"
                \tsay paths of {_t::*}
                \tsay paths of "plain"
                """);
        assertEquals(List.of("home::x=10 home=here name=Steve tags::1=a tags::2=b", "not keyed: plain"), said);
    }

    @Test
    void anUpdatesMapSetsTheIndicesItNamesAndKeepsTheRest() {
        run("""
                on load:
                \tset {_t::kept} to "yes"
                \tset {_t::name} to "Alex"
                \tgraft the tree onto {_t::*}
                \tsay "%{_t::kept}% %{_t::name}% %{_t::home::x}% %{_t::tags::2}%"
                """);
        assertEquals(List.of("yes Steve 10 b"), said);
    }
}
