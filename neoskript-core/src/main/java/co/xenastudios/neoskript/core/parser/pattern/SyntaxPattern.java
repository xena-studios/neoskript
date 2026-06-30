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

    SyntaxPattern(String raw, Pattern regex, int argCount) {
        this.raw = raw;
        this.regex = regex;
        this.argCount = argCount;
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
        Matcher matcher = regex.matcher(input.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        List<String> captures = new ArrayList<>(argCount);
        for (int group = 1; group <= argCount; group++) {
            captures.add(matcher.group(group));
        }
        return Optional.of(captures);
    }

    /** @return the number of {@code %type%} argument slots */
    public int argCount() {
        return argCount;
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
