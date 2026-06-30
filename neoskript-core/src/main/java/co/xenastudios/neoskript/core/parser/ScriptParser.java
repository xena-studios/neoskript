package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Effect;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.EffectEntry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.Trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses script source text into executable {@link Trigger}s.
 *
 * <p>Phase 1 understands top-level {@code on <event>:} blocks whose indented bodies are flat lists
 * of effects. Comments ({@code #}) and blank lines are ignored. Nested sections ({@code if},
 * {@code loop}, …) and other top-level structures (functions, options) arrive in Phase 2.
 */
public final class ScriptParser {

    private final DefaultSyntaxRegistry registry;
    private final EventRegistry events;
    private final ExpressionParser expressions;

    public ScriptParser(DefaultSyntaxRegistry registry, EventRegistry events) {
        this.registry = registry;
        this.events = events;
        this.expressions = new ExpressionParser(registry);
    }

    /** A source line stripped of comments, with its indentation measured. */
    private record Line(String content, int indent, int number) {
    }

    /**
     * Parses an entire script.
     *
     * @param source the full script text
     * @return the triggers it defines
     * @throws ParseException on malformed input
     */
    public List<Trigger> parse(String source) {
        List<Line> lines = readLines(source);
        List<Trigger> triggers = new ArrayList<>();

        int i = 0;
        while (i < lines.size()) {
            Line header = lines.get(i);
            if (header.indent() != 0) {
                throw new ParseException("Unexpected indentation", header.number());
            }

            String lower = header.content().toLowerCase(Locale.ROOT);
            if (!lower.startsWith("on ") || !header.content().endsWith(":")) {
                throw new ParseException("Expected an event ('on <event>:'), got: " + header.content(), header.number());
            }

            String eventName = header.content().substring(3, header.content().length() - 1).trim();
            Class<?> eventClass = events.resolve(eventName)
                    .orElseThrow(() -> new ParseException("Unknown event '" + eventName + "'", header.number()));

            // Collect the indented body.
            List<Effect> effects = new ArrayList<>();
            i++;
            while (i < lines.size() && lines.get(i).indent() > 0) {
                Line bodyLine = lines.get(i);
                if (bodyLine.content().endsWith(":")) {
                    throw new ParseException("Sections are not supported yet: " + bodyLine.content(), bodyLine.number());
                }
                effects.add(parseEffect(bodyLine));
                i++;
            }

            if (effects.isEmpty()) {
                throw new ParseException("Event '" + eventName + "' has an empty body", header.number());
            }
            triggers.add(new Trigger(eventName, eventClass, effects));
        }

        return triggers;
    }

    private Effect parseEffect(Line line) {
        for (EffectEntry entry : registry.effects()) {
            Optional<List<String>> match = entry.pattern().match(line.content());
            if (match.isPresent()) {
                try {
                    return entry.factory().create(new SimpleArguments(expressions.parseArguments(match.get())));
                } catch (ParseException e) {
                    throw new ParseException(e.getMessage(), line.number());
                }
            }
        }
        throw new ParseException("Don't understand the statement '" + line.content() + "'", line.number());
    }

    private static List<Line> readLines(String source) {
        List<Line> lines = new ArrayList<>();
        String[] rawLines = source.split("\n", -1);
        for (int n = 0; n < rawLines.length; n++) {
            String raw = stripComment(rawLines[n]).stripTrailing();
            if (raw.isBlank()) {
                continue;
            }
            int indent = countIndent(raw);
            lines.add(new Line(raw.strip(), indent, n + 1));
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
