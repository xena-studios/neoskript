package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Condition;
import co.xenastudios.neoskript.core.alias.AliasRegistry;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.FunctionCallExpression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.CommandDefinition;
import co.xenastudios.neoskript.core.runtime.CommandRegistry;
import co.xenastudios.neoskript.core.runtime.DefaultVariableStatement;
import co.xenastudios.neoskript.core.runtime.DelayStatement;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.ConditionEntry;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.EffectEntry;
import co.xenastudios.neoskript.core.runtime.EffectStatement;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionDefinition;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.IfSection;
import co.xenastudios.neoskript.core.runtime.SectionValueStatement;
import co.xenastudios.neoskript.core.runtime.LoopSection;
import co.xenastudios.neoskript.core.runtime.Statement;
import co.xenastudios.neoskript.core.runtime.Timespan;
import co.xenastudios.neoskript.core.runtime.Trigger;
import co.xenastudios.neoskript.core.runtime.WhileSection;

import java.util.OptionalLong;
import java.util.Set;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses script source text into executable {@link Trigger}s, populating the {@link FunctionRegistry}
 * with any function definitions it encounters.
 *
 * <p>Supports top-level {@code on <event>:} and {@code function name(params):} blocks. Bodies may
 * contain effects and the {@code if}/{@code else if}/{@code else}, {@code while}, and {@code loop}
 * sections. Comments ({@code #}) and blank lines are ignored.
 */
public final class ScriptParser {

    private static final Pattern LOOP_TIMES = Pattern.compile("loop\\s+(.+)\\s+times", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOP_OVER = Pattern.compile("loop\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUNCTION_HEADER =
            Pattern.compile("function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_HEADER =
            Pattern.compile("command\\s+/?(\\S+).*", Pattern.CASE_INSENSITIVE);

    private final DefaultSyntaxRegistry registry;
    private final EventRegistry events;
    private final FunctionRegistry functions;
    private final CommandRegistry commands;
    private final ExpressionParser expressions;
    private final List<ParseException> errors = new ArrayList<>();

    public ScriptParser(DefaultSyntaxRegistry registry, EventRegistry events, FunctionRegistry functions) {
        this(registry, events, functions, new CommandRegistry());
    }

    public ScriptParser(DefaultSyntaxRegistry registry, EventRegistry events, FunctionRegistry functions,
                        CommandRegistry commands) {
        this.registry = registry;
        this.events = events;
        this.functions = functions;
        this.commands = commands;
        this.expressions = new ExpressionParser(registry, functions);
    }

    /** A node in the indentation tree: a line and its more-deeply-indented children. */
    private record Node(String content, int line, List<Node> children) {
    }

    private record Line(String content, int indent, int number) {
    }

    /** The result of parsing one if/else-if node: the section and the index to continue from. */
    private record IfResult(IfSection section, int next) {
    }

    /**
     * Parses an entire script.
     *
     * @param source the full script text
     * @return the triggers it defines (function definitions are registered as a side effect)
     * @throws ParseException on malformed input
     */
    public List<Trigger> parse(String source) {
        List<Node> roots = applyOptions(buildTree(readLines(source)));
        List<Trigger> triggers = new ArrayList<>();
        errors.clear();

        for (Node node : roots) {
            // Isolate each top-level structure: a parse error in one command/event/function disables
            // only that structure and is collected, so the rest of the script still loads — matching
            // Skript, where one bad trigger never takes down the whole file.
            try {
                String lower = node.content().toLowerCase(Locale.ROOT);
                if (lower.startsWith("every ") && node.content().endsWith(":")) {
                    triggers.add(parsePeriodic(node));
                } else if (lower.startsWith("on ") && node.content().endsWith(":")) {
                    triggers.add(parseEvent(node));
                } else if ((lower.startsWith("function ") || lower.startsWith("local function "))
                        && node.content().endsWith(":")) {
                    parseFunction(node);
                } else if (lower.startsWith("command ") && node.content().endsWith(":")) {
                    parseCommand(node);
                } else if (lower.equals("variables:")) {
                    parseVariables(node).ifPresent(triggers::add);
                } else if (lower.equals("aliases:")) {
                    parseAliases(node);
                } else if (lower.startsWith("import ") || lower.startsWith("using ")) {
                    // `import`/`using` pull in Java types for effect-command use; NeoSkript has no such
                    // reflective bridge, so they are recognised and skipped rather than failing the parse.
                    continue;
                } else {
                    throw new ParseException("Expected an event, function, or command, got: " + node.content(), node.line());
                }
            } catch (ParseException e) {
                errors.add(e);
            }
        }
        return triggers;
    }

    /**
     * @return the per-structure parse errors from the most recent {@link #parse} call — each is a
     * structure (command/event/function) that failed to load while the rest of the script loaded.
     */
    public List<ParseException> errors() {
        return List.copyOf(errors);
    }

    private static final Set<String> LOAD_ALIASES = Set.of("load", "enable", "server load", "server start",
            "script load", "script init", "script enable", "skript start", "skript load");

    private Trigger parseEvent(Node node) {
        String eventName = node.content().substring(3, node.content().length() - 1).trim();
        List<Statement> body = parseBody(node, eventName);
        if (LOAD_ALIASES.contains(eventName.toLowerCase(Locale.ROOT))) {
            return Trigger.onLoad(body);
        }
        EventRegistry.FilteredEvent resolved = events.resolveFiltered(eventName)
                .orElseThrow(() -> new ParseException("Unknown event '" + eventName + "'", node.line()));
        return new Trigger(eventName, resolved.eventClass(), body, resolved.filter());
    }

    private Trigger parsePeriodic(Node node) {
        String spec = node.content().substring("every ".length(), node.content().length() - 1).trim();
        OptionalLong ticks = Timespan.parseTicks(spec);
        if (ticks.isEmpty()) {
            throw new ParseException("Unknown timespan '" + spec + "'", node.line());
        }
        return Trigger.periodic(ticks.getAsLong(), parseBody(node, "every " + spec));
    }

    private List<Statement> parseBody(Node node, String label) {
        List<Statement> body = parseStatements(node.children(), true);
        if (body.isEmpty()) {
            throw new ParseException("'" + label + "' has an empty body", node.line());
        }
        return body;
    }

    private void parseFunction(Node node) {
        Matcher header = FUNCTION_HEADER.matcher(node.content());
        if (!header.find()) {
            throw new ParseException("Malformed function header: " + node.content(), node.line());
        }
        String name = header.group(1);
        List<String> parameters = new ArrayList<>();
        List<Expression<?>> defaults = new ArrayList<>();
        List<String> parameterTypes = new ArrayList<>();
        for (String param : header.group(2).split(",")) {
            String trimmed = param.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // "name: type = default": keep the name and default; the type is best-effort coerced.
            int equals = trimmed.indexOf('=');
            String head = (equals >= 0 ? trimmed.substring(0, equals) : trimmed).trim();
            String defaultExpr = equals >= 0 ? trimmed.substring(equals + 1).trim() : "";
            int colon = head.indexOf(':');
            parameters.add((colon >= 0 ? head.substring(0, colon) : head).trim());
            parameterTypes.add(colon >= 0 ? singular(head.substring(colon + 1).trim()) : null);
            defaults.add(defaultExpr.isEmpty() ? null : expressions.parse(defaultExpr));
        }
        // Optional return type after "::" in the header line (e.g. `function f(...) :: number:`).
        String returnType = null;
        int closeParen = node.content().lastIndexOf(')');
        int marker = closeParen >= 0 ? node.content().indexOf("::", closeParen) : -1;
        if (marker >= 0) {
            returnType = singular(node.content().substring(marker + 2, node.content().length() - 1).trim());
        }
        functions.register(new FunctionDefinition(
                name, parameters, defaults, parameterTypes, returnType, parseStatements(node.children(), false)));
    }

    /** Strips a trailing plural {@code s} from a type name so {@code numbers} reads as {@code number}. */
    private static String singular(String typeName) {
        if (typeName.length() > 1 && typeName.toLowerCase(Locale.ROOT).endsWith("s")) {
            return typeName.substring(0, typeName.length() - 1);
        }
        return typeName.isEmpty() ? null : typeName;
    }

    private Optional<Trigger> parseVariables(Node node) {
        List<Statement> defaults = new ArrayList<>();
        for (Node entry : node.children()) {
            int equals = entry.content().indexOf('=');
            if (equals < 0) {
                throw new ParseException("Expected '{var} = value' in variables block: " + entry.content(),
                        entry.line());
            }
            Expression<?> variable = expressions.parse(entry.content().substring(0, equals).trim());
            if (!(variable instanceof VariableExpression target)) {
                throw new ParseException("Left side of a default must be a variable: " + entry.content(), entry.line());
            }
            Expression<?> value = expressions.parse(entry.content().substring(equals + 1).trim());
            defaults.add(new DefaultVariableStatement(target, value));
        }
        return defaults.isEmpty() ? Optional.empty() : Optional.of(Trigger.onLoad(defaults));
    }

    /** Parses an {@code aliases:} block of {@code name = material} entries into the alias registry. */
    private void parseAliases(Node node) {
        for (Node entry : node.children()) {
            int equals = entry.content().indexOf('=');
            if (equals < 0) {
                throw new ParseException("Expected 'alias = material' in aliases block: " + entry.content(),
                        entry.line());
            }
            String alias = entry.content().substring(0, equals).trim();
            String material = entry.content().substring(equals + 1).trim();
            if (!alias.isEmpty() && !material.isEmpty()) {
                AliasRegistry.register(alias, material);
            }
        }
    }

    private void parseCommand(Node node) {
        String headerLine = node.content().substring(0, node.content().length() - 1).trim();
        Matcher header = COMMAND_HEADER.matcher(headerLine);
        if (!header.matches()) {
            throw new ParseException("Malformed command: " + node.content(), node.line());
        }
        String name = header.group(1);
        String permission = null;
        String description = null;
        String usage = null;
        List<String> aliases = new ArrayList<>();
        List<Statement> body = List.of();

        for (Node child : node.children()) {
            String content = child.content();
            String lower = content.toLowerCase(Locale.ROOT);
            if (lower.equals("trigger:")) {
                // Commands run on the interpreter (see CommandDefinition#run), so `wait` is honoured
                // inside command triggers and their sections, just like event triggers.
                body = parseStatements(child.children(), true);
            } else if (lower.startsWith("permission:")) {
                permission = entryValue(content);
            } else if (lower.startsWith("description:")) {
                description = entryValue(content);
            } else if (lower.startsWith("usage:")) {
                usage = entryValue(content);
            } else if (lower.startsWith("aliases:")) {
                for (String alias : entryValue(content).split(",")) {
                    String trimmed = alias.trim().replaceFirst("^/", "");
                    if (!trimmed.isEmpty()) {
                        aliases.add(trimmed);
                    }
                }
            }
            // Other entries (cooldown, executable by, …) are ignored for now.
        }

        if (body.isEmpty()) {
            throw new ParseException("Command '/" + name + "' has no trigger", node.line());
        }
        commands.register(new CommandDefinition(name, permission, description, usage, aliases, body));
    }

    private static String entryValue(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(colon + 1).trim();
    }

    private List<Statement> parseStatements(List<Node> nodes, boolean allowDelays) {
        List<Statement> result = new ArrayList<>();
        int i = 0;
        while (i < nodes.size()) {
            Node node = nodes.get(i);
            String content = node.content();
            String lower = content.toLowerCase(Locale.ROOT);

            if ((lower.startsWith("wait ") || lower.startsWith("delay ")) && !content.endsWith(":")) {
                result.add(parseDelay(node, allowDelays));
                i++;
            } else if (lower.startsWith("if ") && content.endsWith(":")) {
                IfResult parsed = parseIf(nodes, i, allowDelays);
                result.add(parsed.section());
                i = parsed.next();
            } else if ((lower.startsWith("else if ") || lower.equals("else:")) && content.endsWith(":")) {
                throw new ParseException("'" + content + "' without a matching 'if'", node.line());
            } else if (lower.startsWith("while ") && content.endsWith(":")) {
                Condition condition = parseCondition(content.substring(6, content.length() - 1).trim(), node.line());
                result.add(new WhileSection(condition, parseStatements(node.children(), allowDelays)));
                i++;
            } else if (lower.startsWith("loop ") && content.endsWith(":")) {
                result.add(parseLoop(node, allowDelays));
                i++;
            } else if ((lower.startsWith("for each ") || lower.startsWith("for "))
                    && content.endsWith(":")
                    && FOR_EACH.matcher(content.substring(0, content.length() - 1).trim()).matches()) {
                result.add(parseForEach(node, allowDelays));
                i++;
            } else if (content.endsWith(":") && SECTION_SET.matcher(content).matches()
                    && (lower.contains("potion effect") || lower.contains("damage source"))) {
                result.add(parseSectionSet(node, allowDelays));
                i++;
            } else if (content.endsWith(":")) {
                throw new ParseException("Unknown section: " + content, node.line());
            } else {
                result.add(parseLeaf(node));
                i++;
            }
        }
        return result;
    }

    private Statement parseDelay(Node node, boolean allowDelays) {
        if (!allowDelays) {
            throw new ParseException("'wait' is not supported inside functions", node.line());
        }
        String spec = node.content().substring(node.content().indexOf(' ') + 1).trim();
        OptionalLong ticks = Timespan.parseTicks(spec);
        if (ticks.isEmpty()) {
            throw new ParseException("Unknown timespan '" + spec + "'", node.line());
        }
        return new DelayStatement(ticks.getAsLong());
    }

    private IfResult parseIf(List<Node> nodes, int index, boolean allowDelays) {
        Node node = nodes.get(index);
        String content = node.content();
        boolean elseIf = content.toLowerCase(Locale.ROOT).startsWith("else if ");
        int prefix = elseIf ? "else if ".length() : "if ".length();
        Condition condition = parseCondition(content.substring(prefix, content.length() - 1).trim(), node.line());
        List<Statement> thenBranch = parseStatements(node.children(), allowDelays);

        List<Statement> elseBranch = null;
        int next = index + 1;
        if (next < nodes.size()) {
            Node following = nodes.get(next);
            String followingLower = following.content().toLowerCase(Locale.ROOT);
            if (followingLower.equals("else:")) {
                elseBranch = parseStatements(following.children(), allowDelays);
                next++;
            } else if (followingLower.startsWith("else if ") && following.content().endsWith(":")) {
                IfResult chained = parseIf(nodes, next, allowDelays);
                elseBranch = List.of(chained.section());
                next = chained.next();
            }
        }
        return new IfResult(new IfSection(condition, thenBranch, elseBranch), next);
    }

    private static final Pattern FOR_EACH = Pattern.compile(
            "(?i)for(?:\\s+each)?\\s+(?:value\\s+)?(\\{.+?\\})\\s+in\\s+(.+)");

    /** {@code set <var> to <section value>:} — a section-expression assignment (see {@link SectionValueStatement}). */
    private static final Pattern SECTION_SET = Pattern.compile("(?i)^set\\s+(.+?)\\s+to\\s+(.+):$");

    /**
     * Parses a section-expression assignment: {@code set {var} to <value>:} with an indented body that
     * customises the created value.
     */
    private Statement parseSectionSet(Node node, boolean allowDelays) {
        Matcher matcher = SECTION_SET.matcher(node.content());
        if (!matcher.matches()) {
            throw new ParseException("Malformed section assignment: " + node.content(), node.line());
        }
        if (!(expressions.parse(matcher.group(1).trim()) instanceof VariableExpression target)) {
            throw new ParseException("A section value must be stored in a variable", node.line());
        }
        Expression<?> base = expressions.parse(matcher.group(2).trim());
        List<Statement> body = parseStatements(node.children(), allowDelays);
        return new SectionValueStatement(target, base, body);
    }

    /**
     * Parses {@code for [each] [value] {var} in %objects%:} — a loop that binds each element to the
     * given variable (in addition to {@code loop-value}), matching Skript's for-each section.
     */
    private Statement parseForEach(Node node, boolean allowDelays) {
        String content = node.content().substring(0, node.content().length() - 1).trim();
        Matcher m = FOR_EACH.matcher(content);
        if (!m.matches()) {
            throw new ParseException("Malformed 'for each': " + content, node.line());
        }
        Expression<?> variable = expressions.parse(m.group(1).trim());
        if (!(variable instanceof VariableExpression var)) {
            throw new ParseException("'for each' needs a variable, got: " + m.group(1), node.line());
        }
        Expression<?> values = expressions.parse(m.group(2).trim());
        List<Statement> body = parseStatements(node.children(), allowDelays);
        return LoopSection.forEach(values, body, var::set);
    }

    private Statement parseLoop(Node node, boolean allowDelays) {
        String content = node.content().substring(0, node.content().length() - 1).trim();
        List<Statement> body = parseStatements(node.children(), allowDelays);

        Matcher times = LOOP_TIMES.matcher(content);
        if (times.matches()) {
            return LoopSection.times(expressions.parse(times.group(1).trim()), body);
        }
        Matcher over = LOOP_OVER.matcher(content);
        if (over.matches()) {
            return LoopSection.over(expressions.parse(over.group(1).trim()), body);
        }
        throw new ParseException("Malformed loop: " + content, node.line());
    }

    private static final Pattern SORT = Pattern.compile(
            "(?i)^sort\\s+(\\{.+?})(?:\\s+in\\s+(ascending|descending)\\s+order)?"
                    + "(?:\\s+(?:by|based on)\\s*\\[(.+)])?$");
    private static final Pattern TRANSFORM = Pattern.compile(
            "(?i)^(?:transform|map)\\s+(\\{.+?})\\s+(?:using|with)\\s*\\[(.+)]$");

    private Statement parseLeaf(Node node) {
        String content = node.content();

        // do-if: `<effect> if <condition>` — run the effect only when the condition holds.
        Statement conditional = parseDoIf(node);
        if (conditional != null) {
            return conditional;
        }
        // sort / transform a list variable in place.
        Statement listOp = parseListEffect(node);
        if (listOp != null) {
            return listOp;
        }

        // Overlapping patterns share a leading word (e.g. `send %string%` vs `send actionbar %string%`).
        // If a matching candidate's arguments fail to parse, fall through to the next candidate rather
        // than aborting the line, so the more specific pattern still gets a chance.
        ParseException lastError = null;
        for (EffectEntry entry : registry.effectCandidates(node.content())) {
            Optional<List<String>> match = entry.pattern().match(node.content());
            if (match.isPresent()) {
                try {
                    return new EffectStatement(
                            entry.factory().create(new SimpleArguments(
                                    expressions.parseArguments(match.get(), entry.pattern().argTypes()))));
                } catch (ParseException e) {
                    lastError = e;
                }
            }
        }
        // Fall back to a bare function call used as a statement, e.g. "greet(player)".
        try {
            Expression<?> expression = expressions.parse(node.content());
            if (expression instanceof FunctionCallExpression call) {
                return new EffectStatement(call::getSingle);
            }
        } catch (ParseException ignored) {
            // not an expression either
        }
        if (lastError != null) {
            throw new ParseException(lastError.getMessage(), node.line());
        }
        throw new ParseException("Don't understand the statement '" + node.content() + "'", node.line());
    }

    /** Parses {@code <effect> if <condition>}: the effect runs only when the condition holds. */
    private Statement parseDoIf(Node node) {
        String content = node.content();
        int idx = lastTopLevelIf(content);
        if (idx < 0) {
            return null;
        }
        String head = content.substring(0, idx).trim();
        String tail = content.substring(idx + 4).trim();
        if (head.isEmpty() || tail.isEmpty()) {
            return null;
        }
        try {
            Condition condition = expressions.parseConditionText(tail);
            Statement effect = parseLeaf(new Node(head, node.line(), List.of()));
            return new IfSection(condition, List.of(effect), List.of());
        } catch (ParseException ignored) {
            return null; // not a do-if; parse normally
        }
    }

    /** @return the index of the last top-level {@code " if "} (outside quotes/brackets), or -1. */
    private static int lastTopLevelIf(String s) {
        boolean inQuotes = false;
        int depth = 0;
        int result = -1;
        for (int i = 0; i + 4 <= s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (inQuotes) {
                continue;
            } else if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                depth--;
            } else if (depth == 0 && c == ' ' && s.regionMatches(true, i, " if ", 0, 4)) {
                result = i;
            }
        }
        return result;
    }

    /** Parses {@code sort {list::*} [order] [by [<expr>]]} and {@code (transform|map) {list::*} using [<expr>]}. */
    private Statement parseListEffect(Node node) {
        String content = node.content();
        Matcher sort = SORT.matcher(content);
        if (sort.matches() && expressions.parse(sort.group(1).trim()) instanceof VariableExpression list
                && list.isList()) {
            boolean descending = "descending".equalsIgnoreCase(sort.group(2));
            Expression<?> key = sort.group(3) != null ? expressions.parse(sort.group(3).trim()) : null;
            return ctx -> {
                java.util.List<Object> items =
                        new java.util.ArrayList<>(java.util.Arrays.asList(list.getAll(ctx)));
                Object previous = ctx.getLocal("input");
                try {
                    java.util.Comparator<Object> comparator = (a, b) -> {
                        Object ka = a;
                        Object kb = b;
                        if (key != null) {
                            ctx.setLocal("input", a);
                            ka = key.getSingle(ctx);
                            ctx.setLocal("input", b);
                            kb = key.getSingle(ctx);
                        }
                        return compareValues(ka, kb);
                    };
                    items.sort(descending ? comparator.reversed() : comparator);
                } finally {
                    ctx.setLocal("input", previous);
                }
                replaceListAtomically(ctx, list, items);
            };
        }
        Matcher transform = TRANSFORM.matcher(content);
        if (transform.matches() && expressions.parse(transform.group(1).trim()) instanceof VariableExpression list
                && list.isList()) {
            Expression<?> mapper = expressions.parse(transform.group(2).trim());
            return ctx -> {
                Object[] items = list.getAll(ctx);
                Object previous = ctx.getLocal("input");
                java.util.List<Object> mapped = new java.util.ArrayList<>();
                try {
                    for (Object item : items) {
                        ctx.setLocal("input", item);
                        mapped.add(mapper.getSingle(ctx));
                    }
                } finally {
                    ctx.setLocal("input", previous);
                }
                replaceListAtomically(ctx, list, mapped);
            };
        }
        return null;
    }

    /**
     * Replaces a list variable's contents with {@code items}, atomically for a global list so a
     * concurrent reader/writer (e.g. another region thread on Folia) never sees it half-replaced.
     */
    private static void replaceListAtomically(co.xenastudios.neoskript.api.runtime.TriggerContext ctx,
            VariableExpression list, java.util.List<Object> items) {
        Runnable op = () -> {
            list.delete(ctx);
            items.forEach(item -> list.addToList(ctx, item));
        };
        if (!list.isLocal() && ctx instanceof co.xenastudios.neoskript.core.runtime.VariableScope scope) {
            scope.runAtomic(op);
        } else {
            op.run();
        }
    }

    private static int compareValues(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    private Condition parseCondition(String content, int line) {
        ParseException lastError = null;
        for (ConditionEntry entry : registry.conditionCandidates(content)) {
            Optional<List<String>> match = entry.pattern().match(content);
            if (match.isPresent()) {
                try {
                    return entry.factory().create(new SimpleArguments(
                            expressions.parseArguments(match.get(), entry.pattern().argTypes())));
                } catch (ParseException e) {
                    lastError = e; // try the next overlapping candidate
                }
            }
        }
        if (lastError != null) {
            throw new ParseException(lastError.getMessage(), line);
        }
        throw new ParseException("Don't understand the condition '" + content + "'", line);
    }

    /**
     * Applies an {@code options:} block: collects its {@code key: value} children and substitutes
     * {@code {@key}} occurrences throughout the remaining nodes, then drops the options block.
     */
    private static List<Node> applyOptions(List<Node> roots) {
        Map<String, String> options = new HashMap<>();
        List<Node> remaining = new ArrayList<>();
        for (Node node : roots) {
            if (node.content().equalsIgnoreCase("options:")) {
                for (Node entry : node.children()) {
                    int colon = entry.content().indexOf(':');
                    if (colon > 0) {
                        options.put(entry.content().substring(0, colon).trim(),
                                entry.content().substring(colon + 1).trim());
                    }
                }
            } else {
                remaining.add(node);
            }
        }
        if (options.isEmpty()) {
            return remaining;
        }
        List<Node> substituted = new ArrayList<>(remaining.size());
        for (Node node : remaining) {
            substituted.add(substituteOptions(node, options));
        }
        return substituted;
    }

    private static Node substituteOptions(Node node, Map<String, String> options) {
        String content = node.content();
        for (Map.Entry<String, String> option : options.entrySet()) {
            content = content.replace("{@" + option.getKey() + "}", option.getValue());
        }
        List<Node> children = new ArrayList<>(node.children().size());
        for (Node child : node.children()) {
            children.add(substituteOptions(child, options));
        }
        return new Node(content, node.line(), children);
    }

    private static List<Node> buildTree(List<Line> lines) {
        List<Node> roots = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        Deque<Integer> indents = new ArrayDeque<>();

        for (Line line : lines) {
            Node node = new Node(line.content(), line.number(), new ArrayList<>());
            while (!indents.isEmpty() && indents.peek() >= line.indent()) {
                indents.pop();
                stack.pop();
            }
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.peek().children().add(node);
            }
            stack.push(node);
            indents.push(line.indent());
        }
        return roots;
    }

    private static List<Line> readLines(String source) {
        List<Line> lines = new ArrayList<>();
        String[] rawLines = source.split("\n", -1);
        for (int n = 0; n < rawLines.length; n++) {
            String raw = stripComment(rawLines[n]).stripTrailing();
            if (raw.isBlank()) {
                continue;
            }
            lines.add(new Line(raw.strip(), countIndent(raw), n + 1));
        }
        return lines;
    }

    private static int countIndent(String raw) {
        int indent = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                indent++;
            } else if (c == '\t') {
                indent += 4;
            } else {
                break;
            }
        }
        return indent;
    }

    /** Removes a trailing {@code #} comment, ignoring {@code #} characters inside double quotes. */
    private static String stripComment(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == '#' && !inQuotes) {
                return line.substring(0, i);
            }
        }
        return line;
    }
}
