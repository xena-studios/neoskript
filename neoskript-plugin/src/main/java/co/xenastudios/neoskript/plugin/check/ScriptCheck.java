/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.check;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.Expr;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.parse.ParsedSyntax;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.parse.Statement;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks scripts the way NeoSkript loads them, without running them: their problems as NeoSkript reports them,
 * plus likely Folia hazards and performance smells, which are hints rather than errors.
 */
public final class ScriptCheck {

    /** What a finding is about. */
    public enum Kind {
        /** NeoSkript doesn't load the line: Skript's error for it, or syntax NeoSkript doesn't have. */
        ERROR,
        /** A warning NeoSkript gives as Skript does. */
        WARNING,
        /** Something that works on Paper but may behave differently on Folia. */
        FOLIA,
        /** Something likely to cost a lot of server time. */
        PERFORMANCE
    }

    public record Finding(Kind kind, int line, String message) {}

    public record ScriptReport(String name, List<Finding> findings) {

        /** Whether NeoSkript loads it: it has no errors. */
        public boolean loads() {
            return findings.stream().noneMatch(f -> f.kind() == Kind.ERROR);
        }
    }

    /** Triggers that run on Folia's global region rather than an entity's or a location's. */
    private static final Set<String> GLOBAL_EVENTS =
            Set.of("EvtPeriodical", "EvtAtTime", "EvtRealTime", "ScriptLoad", "EvtScript");
    /** Types of things that live in a region on Folia: their state can only be read on its thread. */
    private static final Set<String> ENTITY_TYPES = Set.of("player", "entity", "livingentity");
    private static final Set<String> PLACE_TYPES = Set.of("block", "location", "chunk");
    /** What can be read of a player or entity from anywhere on Folia. */
    private static final Set<String> SAFE_READS = Set.of("ExprUUID", "PropExprName", "ExprRawName", "ExprIP",
            "ExprPermissions", "ExprWorld", "ExprDisplayName", "ExprOnlinePlayersCount");
    /** Effects that only send something, which Folia allows from anywhere. */
    private static final Set<String> SENDING = Set.of("EffMessage", "EffBroadcast", "EffActionBar", "EffSendTitle",
            "EffLog", "EffPlaySound", "EffSendResourcePack", "EffKick");
    private static final Pattern PERIOD = Pattern.compile("(?i)^every (?:(?:a|an|one|1)\\s+)?(\\d*(?:\\.\\d+)?)\\s*"
            + "(tick|ticks|second|seconds|real seconds?)\\b");
    private static final Pattern READ_MODIFY_WRITE = Pattern.compile("(?i)^set (\\{[^_][^}]*\\}) to \\1 [-+*/] ");

    private final Library library;

    /** Checks scripts with {@code library}, NeoSkript's standard library as the server registered it. */
    public ScriptCheck(Library library) {
        this.library = library;
    }

    /** Checks the scripts together, as NeoSkript loads them, so calls between scripts resolve. */
    public List<ScriptReport> check(List<SourceFile> files) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(files);
        List<Diagnostic> diagnostics = new ArrayList<>();
        parsed.forEach(script -> diagnostics.addAll(script.diagnostics()));
        try {
            CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
            diagnostics.addAll(compiled.diagnostics());
        } catch (RuntimeException | LinkageError e) {
            // Some of what the compiler checks needs a running server; the parse's problems still stand.
        }

        List<ScriptReport> reports = new ArrayList<>();
        for (ParsedScript script : parsed) {
            String name = script.file().name();
            List<Finding> findings = new ArrayList<>();
            for (Diagnostic diagnostic : diagnostics) {
                if (diagnostic.file().equals(name)) {
                    findings.add(new Finding(diagnostic.severity() == Severity.ERROR ? Kind.ERROR : Kind.WARNING,
                            diagnostic.line(), diagnostic.message()));
                }
            }
            for (ParsedStructure structure : script.structures()) {
                hints(structure, findings);
            }
            findings.sort(Comparator.comparingInt(Finding::line).thenComparing(Finding::kind));
            reports.add(new ScriptReport(name, List.copyOf(findings)));
        }
        return reports;
    }

    private void hints(ParsedStructure structure, List<Finding> findings) {
        List<Statement> body;
        boolean global = false;
        switch (structure) {
            case ParsedStructure.Event event -> {
                body = event.body();
                global = GLOBAL_EVENTS.contains(event.event().syntax().id());
                if (event.event().syntax().id().equals("EvtPeriodical")) {
                    period(event.structure().node().line(), body, findings);
                }
            }
            case ParsedStructure.Function function -> body = function.body();
            case ParsedStructure.Parse parse -> {
                parse.code().forEach(code -> hints(code, findings));
                return;
            }
            default -> {
                return;
            }
        }
        walk(body, global, findings);
    }

    /** {@code every 1 tick}: how often it runs, and whether it does something costly each time. */
    private static void period(Line header, List<Statement> body, List<Finding> findings) {
        Matcher matcher = PERIOD.matcher(header.text().strip());
        if (!matcher.find()) {
            return;
        }
        double amount = matcher.group(1).isEmpty() ? 1 : Double.parseDouble(matcher.group(1));
        double ticks = matcher.group(2).toLowerCase(Locale.ROOT).startsWith("tick") ? amount : amount * 20;
        if (ticks < 20 && containsText(body, "all entities", "all players", "all blocks", "blocks in radius")) {
            findings.add(new Finding(Kind.PERFORMANCE, header.number(), "Runs " + times(ticks)
                    + " and goes through players, entities or blocks each time. A longer period, or an event, costs"
                    + " far less."));
        } else if (ticks < 2) {
            findings.add(new Finding(Kind.PERFORMANCE, header.number(), "Runs every tick. Make sure what it does is"
                    + " cheap, or use a longer period or an event."));
        }
    }

    private static String times(double ticks) {
        double perSecond = 20 / ticks;
        return perSecond == Math.rint(perSecond) ? (long) perSecond + " times a second" : "every " + ticks + " ticks";
    }

    private void walk(List<Statement> body, boolean global, List<Finding> findings) {
        for (Statement statement : body) {
            String text = statement.line().text().strip();
            String lower = text.toLowerCase(Locale.ROOT);
            if (READ_MODIFY_WRITE.matcher(text).find()) {
                findings.add(new Finding(Kind.FOLIA, statement.line().number(), "Reading and then setting a global"
                        + " variable isn't one step on Folia, where triggers run at the same time. 'add' and 'remove'"
                        + " change it in one step."));
            }
            if (lower.contains("all entities") && !lower.startsWith("#")) {
                findings.add(new Finding(Kind.PERFORMANCE, statement.line().number(), "Goes through every entity in"
                        + " every world. Entities in a radius, or of one type, are much cheaper."));
            }
            ParsedSyntax syntax = switch (statement) {
                case Statement.Effect effect -> effect.effect();
                case Statement.Condition condition -> condition.condition();
                case Statement.Section section -> section.header();
                default -> null;
            };
            if (global && syntax != null) {
                boolean changes = syntax.syntax().kind() == co.xenastudios.neoskript.frontend.registry.SyntaxKind.EFFECT
                        && !SENDING.contains(syntax.syntax().id()) && hasArgument(syntax, ENTITY_TYPES);
                if (!changes && readsRegions(syntax)) {
                    findings.add(new Finding(Kind.FOLIA, statement.line().number(), "Reads a player's, an entity's or"
                            + " a block's state in a trigger that runs on Folia's global region, where it can't be"
                            + " read."));
                } else if (changes) {
                    findings.add(new Finding(Kind.FOLIA, statement.line().number(), "Changes players or entities from"
                            + " Folia's global region. NeoSkript sends the change to their region, so it happens a"
                            + " moment later, after the rest of the trigger."));
                }
            }
            if (statement instanceof Statement.Section section) {
                if (lower.startsWith("while ") && containsWaitOfOneTick(section.body())) {
                    findings.add(new Finding(Kind.PERFORMANCE, statement.line().number(), "Checks its condition every"
                            + " tick. An event that fires when the condition changes costs far less."));
                }
                walk(section.body(), global, findings);
            } else if (statement instanceof Statement.Parse parse) {
                walk(parse.body(), global, findings);
            }
        }
    }

    /** Whether the syntax, or any expression in it, reads the state of something that lives in a region. */
    private static boolean readsRegions(ParsedSyntax syntax) {
        for (Object value : syntax.match().values()) {
            if (value instanceof Expr expr && readsRegions(expr) || value instanceof ParsedSyntax inner && readsRegions(inner)) {
                return true;
            }
        }
        for (Object value : syntax.match().regexValues()) {
            // A section's condition: 'if block at location of loop-player is stone'.
            if (value instanceof ParsedSyntax inner && readsRegions(inner)) {
                return true;
            }
        }
        return false;
    }

    private static boolean readsRegions(Expr expr) {
        return switch (expr) {
            case Expr.Syntax syntax -> !SAFE_READS.contains(syntax.syntax().syntax().id())
                    && (PLACE_TYPES.contains(syntax.type()) && !syntax.syntax().match().values().isEmpty()
                            || hasArgument(syntax.syntax(), ENTITY_TYPES) || hasArgument(syntax.syntax(), PLACE_TYPES))
                    || readsRegions(syntax.syntax());
            case Expr.ListOf list -> list.items().stream().anyMatch(ScriptCheck::readsRegions);
            case Expr.FunctionCall call -> call.arguments().stream().anyMatch(ScriptCheck::readsRegions);
            case Expr.Text text -> text.embedded().stream().anyMatch(ScriptCheck::readsRegions);
            default -> false;
        };
    }

    /** Whether {@code expr} is a loop value named for one of {@code types}: 'loop-player' in 'loop all players'. */
    private static boolean loopValueOf(Expr.Syntax expr, Set<String> types) {
        if (!expr.syntax().syntax().id().equals("ExprLoopValue")) {
            return false;
        }
        for (Object value : expr.syntax().match().regexValues()) {
            String name = (value instanceof java.util.regex.MatchResult match ? match.group() : String.valueOf(value))
                    .toLowerCase(Locale.ROOT);
            if (types.stream().anyMatch(name::contains)) {
                return true;
            }
        }
        return false;
    }

    /** Whether one of the syntax's arguments is of one of {@code types}. */
    private static boolean hasArgument(ParsedSyntax syntax, Set<String> types) {
        for (Object value : syntax.match().values()) {
            if (value instanceof Expr.Syntax argument && (types.contains(argument.type()) || loopValueOf(argument, types))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWaitOfOneTick(List<Statement> body) {
        for (Statement statement : body) {
            if (statement.line().text().strip().toLowerCase(Locale.ROOT).matches("wait (a|1|one) tick")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsText(List<Statement> body, String... words) {
        for (Statement statement : body) {
            String lower = statement.line().text().toLowerCase(Locale.ROOT);
            for (String word : words) {
                if (lower.contains(word)) {
                    return true;
                }
            }
            if (statement instanceof Statement.Section section && containsText(section.body(), words)) {
                return true;
            }
        }
        return false;
    }
}
