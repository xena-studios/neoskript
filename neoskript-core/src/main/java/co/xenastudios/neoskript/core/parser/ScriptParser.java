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

        for (Node node : roots) {
            String lower = node.content().toLowerCase(Locale.ROOT);
            if (lower.startsWith("every ") && node.content().endsWith(":")) {
                triggers.add(parsePeriodic(node));
            } else if (lower.startsWith("on ") && node.content().endsWith(":")) {
                triggers.add(parseEvent(node));
            } else if (lower.startsWith("function ") && node.content().endsWith(":")) {
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
        }
        return triggers;
    }

    private static final Set<String> LOAD_ALIASES = Set.of("load", "enable", "server load", "server start");

    private Trigger parseEvent(Node node) {
        String eventName = node.content().substring(3, node.content().length() - 1).trim();
        List<Statement> body = parseBody(node, eventName);
        if (LOAD_ALIASES.contains(eventName.toLowerCase(Locale.ROOT))) {
            return Trigger.onLoad(body);
        }
        Class<?> eventClass = events.resolve(eventName)
                .orElseThrow(() -> new ParseException("Unknown event '" + eventName + "'", node.line()));
        return new Trigger(eventName, eventClass, body);
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
        for (String param : header.group(2).split(",")) {
            String trimmed = param.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Accept "name: type = default" — keep the name and default; the type is not yet enforced.
            int equals = trimmed.indexOf('=');
            String head = (equals >= 0 ? trimmed.substring(0, equals) : trimmed).trim();
            String defaultExpr = equals >= 0 ? trimmed.substring(equals + 1).trim() : "";
            int colon = head.indexOf(':');
            parameters.add((colon >= 0 ? head.substring(0, colon) : head).trim());
            defaults.add(defaultExpr.isEmpty() ? null : expressions.parse(defaultExpr));
        }
        functions.register(
                new FunctionDefinition(name, parameters, defaults, parseStatements(node.children(), false)));
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
                body = parseStatements(child.children(), false);
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
            throw new ParseException(
                    "'wait' is only supported at the top level of a trigger (not inside sections or functions yet)",
                    node.line());
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

    private Statement parseLeaf(Node node) {
        for (EffectEntry entry : registry.effectCandidates(node.content())) {
            Optional<List<String>> match = entry.pattern().match(node.content());
            if (match.isPresent()) {
                try {
                    return new EffectStatement(
                            entry.factory().create(new SimpleArguments(expressions.parseArguments(match.get()))));
                } catch (ParseException e) {
                    throw new ParseException(e.getMessage(), node.line());
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
        throw new ParseException("Don't understand the statement '" + node.content() + "'", node.line());
    }

    private Condition parseCondition(String content, int line) {
        for (ConditionEntry entry : registry.conditionCandidates(content)) {
            Optional<List<String>> match = entry.pattern().match(content);
            if (match.isPresent()) {
                try {
                    return entry.factory().create(new SimpleArguments(expressions.parseArguments(match.get())));
                } catch (ParseException e) {
                    throw new ParseException(e.getMessage(), line);
                }
            }
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
