package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Condition;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.FunctionCallExpression;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.ConditionEntry;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.EffectEntry;
import co.xenastudios.neoskript.core.runtime.EffectStatement;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionDefinition;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.IfSection;
import co.xenastudios.neoskript.core.runtime.LoopSection;
import co.xenastudios.neoskript.core.runtime.Statement;
import co.xenastudios.neoskript.core.runtime.Trigger;
import co.xenastudios.neoskript.core.runtime.WhileSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
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

    private final DefaultSyntaxRegistry registry;
    private final EventRegistry events;
    private final FunctionRegistry functions;
    private final ExpressionParser expressions;

    public ScriptParser(DefaultSyntaxRegistry registry, EventRegistry events, FunctionRegistry functions) {
        this.registry = registry;
        this.events = events;
        this.functions = functions;
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
        List<Node> roots = buildTree(readLines(source));
        List<Trigger> triggers = new ArrayList<>();

        for (Node node : roots) {
            String lower = node.content().toLowerCase(Locale.ROOT);
            if (lower.startsWith("on ") && node.content().endsWith(":")) {
                triggers.add(parseEvent(node));
            } else if (lower.startsWith("function ") && node.content().endsWith(":")) {
                parseFunction(node);
            } else {
                throw new ParseException("Expected an event or function, got: " + node.content(), node.line());
            }
        }
        return triggers;
    }

    private Trigger parseEvent(Node node) {
        String eventName = node.content().substring(3, node.content().length() - 1).trim();
        Class<?> eventClass = events.resolve(eventName)
                .orElseThrow(() -> new ParseException("Unknown event '" + eventName + "'", node.line()));
        List<Statement> body = parseStatements(node.children());
        if (body.isEmpty()) {
            throw new ParseException("Event '" + eventName + "' has an empty body", node.line());
        }
        return new Trigger(eventName, eventClass, body);
    }

    private void parseFunction(Node node) {
        Matcher header = FUNCTION_HEADER.matcher(node.content());
        if (!header.find()) {
            throw new ParseException("Malformed function header: " + node.content(), node.line());
        }
        String name = header.group(1);
        List<String> parameters = new ArrayList<>();
        for (String param : header.group(2).split(",")) {
            String trimmed = param.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Accept "name: type" — keep the name, ignore the (not-yet-enforced) type.
            int colon = trimmed.indexOf(':');
            parameters.add((colon >= 0 ? trimmed.substring(0, colon) : trimmed).trim());
        }
        functions.register(new FunctionDefinition(name, parameters, parseStatements(node.children())));
    }

    private List<Statement> parseStatements(List<Node> nodes) {
        List<Statement> result = new ArrayList<>();
        int i = 0;
        while (i < nodes.size()) {
            Node node = nodes.get(i);
            String content = node.content();
            String lower = content.toLowerCase(Locale.ROOT);

            if (lower.startsWith("if ") && content.endsWith(":")) {
                IfResult parsed = parseIf(nodes, i);
                result.add(parsed.section());
                i = parsed.next();
            } else if ((lower.startsWith("else if ") || lower.equals("else:")) && content.endsWith(":")) {
                throw new ParseException("'" + content + "' without a matching 'if'", node.line());
            } else if (lower.startsWith("while ") && content.endsWith(":")) {
                Condition condition = parseCondition(content.substring(6, content.length() - 1).trim(), node.line());
                result.add(new WhileSection(condition, parseStatements(node.children())));
                i++;
            } else if (lower.startsWith("loop ") && content.endsWith(":")) {
                result.add(parseLoop(node));
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

    private IfResult parseIf(List<Node> nodes, int index) {
        Node node = nodes.get(index);
        String content = node.content();
        boolean elseIf = content.toLowerCase(Locale.ROOT).startsWith("else if ");
        int prefix = elseIf ? "else if ".length() : "if ".length();
        Condition condition = parseCondition(content.substring(prefix, content.length() - 1).trim(), node.line());
        List<Statement> thenBranch = parseStatements(node.children());

        List<Statement> elseBranch = null;
        int next = index + 1;
        if (next < nodes.size()) {
            Node following = nodes.get(next);
            String followingLower = following.content().toLowerCase(Locale.ROOT);
            if (followingLower.equals("else:")) {
                elseBranch = parseStatements(following.children());
                next++;
            } else if (followingLower.startsWith("else if ") && following.content().endsWith(":")) {
                IfResult chained = parseIf(nodes, next);
                elseBranch = List.of(chained.section());
                next = chained.next();
            }
        }
        return new IfResult(new IfSection(condition, thenBranch, elseBranch), next);
    }

    private Statement parseLoop(Node node) {
        String content = node.content().substring(0, node.content().length() - 1).trim();
        List<Statement> body = parseStatements(node.children());

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
