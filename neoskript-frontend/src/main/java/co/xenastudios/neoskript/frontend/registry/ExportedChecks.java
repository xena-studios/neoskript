/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import co.xenastudios.neoskript.frontend.parse.Expr;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.parse.ParsedSyntax;
import co.xenastudios.neoskript.frontend.parse.Statement;
import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import co.xenastudios.neoskript.frontend.pattern.PatternElement;
import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.structure.Line;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Errors a server's compiler gives for code the parser lets through, for a registry {@link ExportedRegistry} built:
 * what the in-browser parser adds to the parser's own. Two kinds, in the places the compiler words them as below:
 *
 * <ul>
 *   <li>An event value the event doesn't have ({@code event-block} in {@code on damage}), with the error the
 *       server's compiler gives, which the export holds ({@link SyntaxExport.Scope#missing}). As on the server, an
 *       excluded value's own error is for the first time it's asked for.
 *   <li>Words where only an item, block or entity fits that aren't one, in an effect that isn't a change ({@code
 *       spawn a zombi at player}): {@code Can't understand this condition/effect: ...}, or the error the parser kept
 *       for them. Item names in a change ({@code give player 1 stne}) are left out: the server's error there names
 *       what the target takes, which only the server knows.
 * </ul>
 *
 * <p>It errs on the side of saying nothing: in sections that may give values of their own, in a comparison, and
 * for a value before or after the event, it doesn't check event values. The {@code parser-parity} harness scenario
 * compares what it reports with a server's errors.
 */
public final class ExportedChecks {

    private final SyntaxRegistry registry;
    private final ExportedRegistry.Values values;
    private final Set<String> exact;
    private String file;
    private final List<Diagnostic> found = new ArrayList<>();
    private final Set<Integer> reported = new HashSet<>();
    /** The excluded values already asked for, by scope, type and plurality. */
    private final Set<String> asked = new HashSet<>();
    private String source;
    private boolean header;
    private static final java.util.regex.Pattern CONDITIONAL = java.util.regex.Pattern.compile("(?i)^\\s*(?:else )?if ");

    private ExportedChecks(SyntaxRegistry registry, ExportedRegistry.Values values) {
        this.registry = registry;
        this.values = values;
        this.exact = values.exactTypes;
    }

    /**
     * The errors in {@code script}, parsed with {@code registry}, that the server's compiler would add, one a line,
     * on lines the parser found nothing wrong with. None if {@code registry} isn't one {@link ExportedRegistry}
     * built.
     */
    public static List<Diagnostic> check(SyntaxRegistry registry, ParsedScript script) {
        return check(registry, List.of(script));
    }

    /**
     * {@link #check(SyntaxRegistry, ParsedScript)} for scripts loaded together, in the order the server sets them
     * up: an excluded value's own error is for the first of them that asks for it.
     */
    public static List<Diagnostic> check(SyntaxRegistry registry, List<ParsedScript> scripts) {
        if (!(registry.eventValues() instanceof ExportedRegistry.Values values)) {
            return List.of();
        }
        ExportedChecks checks = new ExportedChecks(registry, values);
        for (ParsedScript script : scripts) {
            checks.file = script.file().name();
            checks.reported.clear();
            for (Diagnostic diagnostic : script.diagnostics()) {
                checks.reported.add(diagnostic.line());
            }
            for (ParsedStructure structure : script.structures()) {
                checks.structure(structure);
            }
        }
        return List.copyOf(checks.found);
    }

    private void structure(ParsedStructure structure) {
        switch (structure) {
            case ParsedStructure.Event event -> statements(event.body(),
                    values.event(event.event().syntax()) instanceof ExportedRegistry.Values.NamedScope named ? named : null);
            // As the server's compiler calls them.
            case ParsedStructure.Command command -> statements(command.statements(),
                    values.named(values.command(), "a command event"));
            case ParsedStructure.Function function -> statements(function.body(),
                    values.named(values.function(), "a function event"));
            case ParsedStructure.Custom custom -> statements(custom.body(), null);
            case ParsedStructure.Parse parse -> parse.code().forEach(this::structure);
            default -> {}
        }
    }

    private void statements(List<Statement> statements, ExportedRegistry.Values.NamedScope scope) {
        if (statements == null) {
            return;
        }
        for (Statement statement : statements) {
            switch (statement) {
                case Statement.Effect effect -> {
                    line(effect.line(), effect.effect(), scope);
                    if (!reported.contains(effect.line().number()) && !changes(effect.effect())) {
                        unknownWords(effect.line(), effect.effect());
                    }
                }
                case Statement.Condition condition -> line(condition.line(), condition.condition(), scope);
                case Statement.Section section -> {
                    if (section.header() != null) {
                        line(section.line(), section.header(), scope);
                    }
                    boolean keeps = section.header() != null && values.keepsEventValues(section.header().syntax());
                    statements(section.body(), keeps ? scope : null);
                }
                case Statement.Conditions conditions -> {
                    for (int i = 0; i < conditions.conditions().size() && i < conditions.conditionLines().size(); i++) {
                        line(conditions.conditionLines().get(i), conditions.conditions().get(i), scope);
                    }
                }
                case Statement.Parse parse -> statements(parse.body(), scope);
                default -> {}
            }
        }
    }

    // ------------------------------------------------------------------ event values

    /** Checks the event values in {@code syntax}, a line's own element. */
    private void line(Line line, ParsedSyntax syntax, ExportedRegistry.Values.NamedScope scope) {
        if (scope == null || reported.contains(line.number())) {
            return;
        }
        source = line.text();
        header = line.opensSection();
        String error = eventValues(syntax, scope, changes(syntax));
        if (error != null) {
            report(line, error);
        }
    }

    /**
     * The first error for an event value in {@code syntax}'s values, or {@code null}. In a {@code change}, the value
     * is read as what the target takes, never as any object.
     */
    private String eventValues(ParsedSyntax syntax, ExportedRegistry.Values.NamedScope scope, boolean change) {
        if (syntax.syntax().id().equals("ExprTimeState")) {
            // A value before or after the event: resolved otherwise.
            return null;
        }
        List<Object> matched = syntax.match().values();
        PlaceholderSpec[] specs = specs(syntax.syntax().patterns().get(syntax.pattern()));
        for (int i = 0; i < matched.size(); i++) {
            String error = eventValues(matched.get(i), i < specs.length && !change ? specs[i] : null, scope);
            if (error != null) {
                return error;
            }
        }
        for (Object value : syntax.match().regexValues()) {
            if (value instanceof ParsedSyntax inner) {
                String error = eventValues(inner, scope, false);
                if (error != null) {
                    return error;
                }
            }
        }
        return null;
    }

    private String eventValues(Object value, PlaceholderSpec spec, ExportedRegistry.Values.NamedScope scope) {
        return switch (value) {
            case Expr.Syntax syntax -> {
                String error = eventValue(syntax, spec, scope);
                yield error != null ? error : eventValues(syntax.syntax(), scope, false);
            }
            case Expr.ListOf list -> {
                for (Expr item : list.items()) {
                    String error = eventValues(item, spec, scope);
                    if (error != null) {
                        yield error;
                    }
                }
                yield null;
            }
            case Expr.Text text -> {
                for (Expr embedded : text.embedded()) {
                    String error = eventValues(embedded, null, scope);
                    if (error != null) {
                        yield error;
                    }
                }
                yield null;
            }
            case Expr.FunctionCall call -> {
                for (Expr argument : call.arguments()) {
                    String error = eventValues(argument, null, scope);
                    if (error != null) {
                        yield error;
                    }
                }
                yield null;
            }
            case null, default -> null;
        };
    }

    /** The error for {@code syntax} if it's an event value the event doesn't have, or {@code null}. */
    private String eventValue(Expr.Syntax syntax, PlaceholderSpec spec, ExportedRegistry.Values.NamedScope scope) {
        SyntaxInfo info = syntax.syntax().syntax();
        if (!values.isEventValue(info) || syntax.syntax().match().regexValues().isEmpty()
                || !(syntax.syntax().match().regexValues().getFirst() instanceof TypeRef type)) {
            return null;
        }
        String words = syntax.end() <= source.length() && syntax.start() >= 0
                ? source.substring(syntax.start(), syntax.end()).strip() : "";
        if (scope.allows(info, type, words)) {
            return null;
        }
        String lower = words.toLowerCase(Locale.ROOT);
        if (info.id().equals("ExprEventValue") && spec != null && spec.types().stream().anyMatch(name -> {
            TypeRef ref = registry.resolveType(name);
            return ref != null && ref.type().id().equals(SyntaxRegistry.OBJECT);
        }) && !lower.startsWith("the ") && !lower.startsWith("event-")) {
            // Where any object fits, the server may read the words as another value ('entity' is the entity type).
            return null;
        }
        boolean first = asked.add(scope.index() + "/" + type.type().id() + "/" + type.plural());
        java.util.regex.Matcher conditional = CONDITIONAL.matcher(source);
        if (header && conditional.lookingAt() && syntax.start() > conditional.end()) {
            // A failing 'if' is read again as a whole line, which asks again: the error is the second ask's.
            first = false;
        }
        return scope.data.error(info, type, scope.description, first);
    }

    // ------------------------------------------------------------------ unknown words

    /** Whether {@code effect} changes a value, where the server's errors for its value say what the target takes. */
    private static boolean changes(ParsedSyntax effect) {
        return effect.syntax().patterns().get(effect.pattern()).source().contains("%~");
    }

    /** Reports words in {@code effect} where only a name fits that no name is. */
    private void unknownWords(Line line, ParsedSyntax effect) {
        if (exact.isEmpty()) {
            return;
        }
        Expr.Unresolved words = unknown(effect);
        if (words != null) {
            report(line, words.problem() != null ? words.problem()
                    : "Can't understand this condition/effect: " + line.key().strip());
        }
    }

    private Expr.Unresolved unknown(ParsedSyntax syntax) {
        List<Object> matched = syntax.match().values();
        PlaceholderSpec[] specs = specs(syntax.syntax().patterns().get(syntax.pattern()));
        for (int i = 0; i < matched.size(); i++) {
            Expr.Unresolved words = unknown(matched.get(i), i < specs.length ? specs[i] : null);
            if (words != null) {
                return words;
            }
        }
        return null;
    }

    private Expr.Unresolved unknown(Object value, PlaceholderSpec spec) {
        return switch (value) {
            case Expr.Unresolved words -> namesOnly(spec, words.text()) && noName(words.text()) ? words : null;
            case Expr.ListOf list -> {
                for (Expr item : list.items()) {
                    Expr.Unresolved words = unknown(item, spec);
                    if (words != null) {
                        yield words;
                    }
                }
                yield null;
            }
            case Expr.Syntax syntax -> unknown(syntax.syntax());
            case null, default -> null;
        };
    }

    /**
     * Whether only items, blocks or entities can be written as words where {@code spec} is, whose names the export
     * holds: each of its types is one of those, or has no words for its values at all.
     */
    private boolean namesOnly(PlaceholderSpec spec, String words) {
        if (spec == null || spec.types().isEmpty()) {
            return false;
        }
        boolean names = false;
        for (String name : spec.types()) {
            TypeRef ref = registry.resolveType(name);
            if (ref == null) {
                return false;
            }
            TypeInfo type = ref.type();
            if (exact.contains(type.id())) {
                names = true;
            } else if (type.openLiterals() || !type.literals().isEmpty() || registry.parses(type, words)
                    || type.id().equals(SyntaxRegistry.OBJECT)) {
                return false;
            }
        }
        return names;
    }

    /** Whether no item, block or entity is called {@code text}, and no type has it among its literals. */
    private boolean noName(String text) {
        String normal = SyntaxRegistry.normalize(text);
        for (TypeInfo type : registry.types()) {
            if (type.literals().contains(normal)) {
                return false;
            }
            if (exact.contains(type.id()) && registry.mightBeLiteral(type, text)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private void report(Line line, String message) {
        if (reported.add(line.number())) {
            found.add(new Diagnostic(Severity.ERROR, file, line.number(), line.column(), 0, message));
        }
    }

    /** A pattern's placeholders, by index. */
    private static PlaceholderSpec[] specs(CompiledPattern pattern) {
        PlaceholderSpec[] specs = new PlaceholderSpec[pattern.placeholderCount()];
        collect(pattern.root(), specs);
        return specs;
    }

    private static void collect(PatternElement element, PlaceholderSpec[] specs) {
        switch (element) {
            case PatternElement.Placeholder placeholder -> specs[placeholder.index()] = placeholder.spec();
            case PatternElement.Sequence sequence -> sequence.elements().forEach(e -> collect(e, specs));
            case PatternElement.Choice choice -> choice.alternatives().forEach(a -> collect(a.body(), specs));
            case PatternElement.Optional optional -> collect(optional.choice(), specs);
            default -> {}
        }
    }
}
