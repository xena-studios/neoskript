package co.xenastudios.neoskript.core.parser.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A compiled syntax pattern. Produced by {@link PatternCompiler} and matched against a line (or
 * argument substring) to capture the argument text at each {@code %type%} slot.
 *
 * <p>Phase 1 backs matching with a precompiled {@link Pattern}. This is compiled once at load time,
 * so the cost is paid during parsing, not at runtime. Phase 3 swaps this for the token-indexed
 * dispatch described in the plan, for faster parsing on large script sets; the matching contract is
 * unchanged.
 */
public final class SyntaxPattern {

    private final String raw;
    private final Pattern regex;
    private final int argCount;
    private final String firstLiteral;

    SyntaxPattern(String raw, Pattern regex, int argCount, String firstLiteral) {
        this.raw = raw;
        this.regex = regex;
        this.argCount = argCount;
        this.firstLiteral = firstLiteral;
    }

    /**
     * Attempts to match the given input against this pattern.
     *
     * @param input the line or substring to match (matched whole, after trimming)
     * @return the captured argument strings (one per {@code %type%} slot, {@code null} for an
     *         optional slot that did not match), or {@link Optional#empty()} if the input does not
     *         match
     */
    public Optional<List<String>> match(String input) {
        // Mask quoted strings to placeholders so the matcher treats them as atomic — otherwise a
        // literal/optional in the pattern (e.g. " to ") could match text inside a string argument
        // like "Welcome to the server!".
        List<String> quotes = new ArrayList<>();
        String masked = maskQuotes(input.trim(), quotes);

        Matcher matcher = regex.matcher(masked);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        List<String> captures = new ArrayList<>(argCount);
        for (int group = 1; group <= argCount; group++) {
            captures.add(unmaskQuotes(matcher.group(group), quotes));
        }
        return Optional.of(captures);
    }

    private static final char MASK = '\u0001';

    private static String maskQuotes(String input, List<String> quotes) {
        StringBuilder out = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '"') {
                int end = input.indexOf('"', i + 1);
                if (end < 0) {
                    end = input.length() - 1; // unterminated: mask to the end
                }
                quotes.add(input.substring(i, Math.min(end + 1, input.length())));
                out.append(MASK).append(quotes.size() - 1).append(MASK);
                i = end + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String unmaskQuotes(String captured, List<String> quotes) {
        if (captured == null || quotes.isEmpty() || captured.indexOf(MASK) < 0) {
            return captured;
        }
        String result = captured;
        for (int i = 0; i < quotes.size(); i++) {
            result = result.replace(MASK + Integer.toString(i) + MASK, quotes.get(i));
        }
        return result;
    }

    /** @return the number of {@code %type%} argument slots */
    public int argCount() {
        return argCount;
    }

    /**
     * @return the lowercased leading literal word of this pattern (used to index candidates for fast
     *         dispatch), or {@code null} if the pattern begins with an argument or alternation
     */
    public String firstLiteral() {
        return firstLiteral;
    }

    /** @return the original pattern string */
    public String raw() {
        return raw;
    }

    @Override
    public String toString() {
        return "SyntaxPattern[" + raw + "]";
    }
}
