package co.xenastudios.neoskript.core.parser.pattern;

import java.util.regex.Pattern;

/**
 * Compiles Skript-style pattern strings into {@link SyntaxPattern}s.
 *
 * <p>Supported syntax:
 * <ul>
 *   <li>{@code %type%} — an argument slot. The type name is informational in Phase 1; the slot
 *       captures one expression's text. Becomes a capturing group.</li>
 *   <li>{@code [optional]} — an optional group (may nest). A leading space is pulled inside the
 *       group so the surrounding whitespace is also optional.</li>
 *   <li>{@code (a|b)} — alternatives.</li>
 *   <li>runs of whitespace — match one-or-more whitespace characters.</li>
 *   <li>any other character — matched literally (case-insensitively).</li>
 * </ul>
 */
public final class PatternCompiler {

    private static final String REGEX_SPECIALS = "\\.^$+{}=!<>?*";

    private PatternCompiler() {
    }

    /**
     * Compiles a pattern string.
     *
     * @param pattern the pattern, e.g. {@code "send %string% [to %player%]"}
     * @return the compiled pattern
     * @throws IllegalArgumentException if the pattern is malformed (e.g. an unclosed {@code %})
     */
    public static SyntaxPattern compile(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        int argCount = 0;
        // The declared base type name of each `%type%` slot, in order (modifiers -/*/~ stripped). Used
        // so slots such as `%classinfo%` can be resolved to a type reference instead of an expression.
        java.util.List<String> argTypes = new java.util.ArrayList<>();
        int i = 0;
        int length = pattern.length();
        // An optional group `[...]` absorbs exactly ONE adjacent space so that the separator around it
        // stays balanced when the group is omitted:
        //   - If a required space precedes it (the regex already ends with `\s+`, e.g. after `x ` in
        //     `x [component] of`), absorb THAT space and keep the following one required:
        //     `x(?:\s+component)?\s+of` matches "x of".
        //   - Else if it is a standalone "word" optional (preceded by the pattern start, whitespace, or
        //     another group), absorb the FOLLOWING space: `[the] [new] block` -> `(?:the\s+)?(?:new\s+)?
        //     block` matches "block".
        //   - Else it is attached to a preceding literal token (`max[imum] players`, `x[-coordinate]
        //     of`); the following space is a real separator, so absorb nothing: `max(?:imum)?\s+players`.
        // Each Boolean on the stack records whether the group should absorb its FOLLOWING space on close.
        java.util.Deque<Boolean> absorbFollowing = new java.util.ArrayDeque<>();

        while (i < length) {
            char c = pattern.charAt(i);
            switch (c) {
                case '%' -> {
                    int end = pattern.indexOf('%', i + 1);
                    if (end < 0) {
                        throw new IllegalArgumentException("Unclosed '%' in pattern: " + pattern);
                    }
                    regex.append("(.+?)");
                    argCount++;
                    // Record the slot's base type name (e.g. `%-*classinfo%` -> "classinfo").
                    argTypes.add(pattern.substring(i + 1, end)
                            .replaceFirst("^[-*~]+", "").toLowerCase(java.util.Locale.ROOT));
                    i = end + 1;
                }
                case '[' -> {
                    // An optional compiles to `(?:(?:CONTENT)...)?`; the inner group means an absorbed
                    // space binds to the whole content even when it is an alternation (`[vector|
                    // quaternion] x` -> `(?:(?:vector|quaternion)\s+)?x`, not `(?:vector|quaternion\s+)`).
                    boolean preceding = endsWith(regex, "\\s+");
                    char prev = i == 0 ? ' ' : pattern.charAt(i - 1);
                    // A group is "word-level" (not attached to a preceding literal) when it starts the
                    // pattern, follows whitespace, or begins an alternation branch or sub-group: after
                    // `(`/`|`/`]`/`)` there is no preceding literal token, so `([x] y|z)` treats `[x]`
                    // as word-level and its trailing space as optional (matching "y" as well as "x y").
                    boolean attached = !(i == 0 || Character.isWhitespace(prev)
                            || prev == ']' || prev == ')' || prev == '(' || prev == '|');
                    if (preceding) {
                        regex.setLength(regex.length() - 3);
                        regex.append("(?:\\s+(?:");
                    } else {
                        regex.append("(?:(?:");
                    }
                    // Absorb the following space only for a standalone word optional that did not already
                    // absorb a preceding space; an attached suffix optional keeps it as a separator.
                    absorbFollowing.push(!preceding && !attached);
                    i++;
                }
                case ']' -> {
                    boolean following = !absorbFollowing.isEmpty() && absorbFollowing.pop();
                    if (following && i + 1 < length && Character.isWhitespace(pattern.charAt(i + 1))) {
                        regex.append(")\\s+)?");
                        i++;
                        while (i < length && Character.isWhitespace(pattern.charAt(i))) {
                            i++;
                        }
                    } else {
                        regex.append("))?");
                        i++;
                    }
                }
                case '\\' -> {
                    // A backslash escapes the next character as a literal (Skript uses `\(`, `\)` for
                    // literal parentheses, since `(` normally opens an alternation).
                    i++;
                    if (i < length) {
                        char escaped = pattern.charAt(i);
                        if (REGEX_SPECIALS.indexOf(escaped) >= 0 || "()[]|".indexOf(escaped) >= 0) {
                            regex.append('\\');
                        }
                        regex.append(escaped);
                        i++;
                    }
                }
                case '(' -> {
                    regex.append("(?:");
                    i++;
                }
                case ')' -> {
                    regex.append(')');
                    i++;
                }
                case '|' -> {
                    regex.append('|');
                    i++;
                }
                default -> {
                    if (Character.isWhitespace(c)) {
                        while (i < length && Character.isWhitespace(pattern.charAt(i))) {
                            i++;
                        }
                        regex.append("\\s+");
                    } else {
                        if (REGEX_SPECIALS.indexOf(c) >= 0) {
                            regex.append('\\');
                        }
                        regex.append(c);
                        i++;
                    }
                }
            }
        }

        regex.append('$');
        return new SyntaxPattern(pattern, Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE),
                argCount, leadingLiteral(pattern), java.util.List.copyOf(argTypes));
    }

    /**
     * Extracts the lowercased leading literal word of a pattern, or {@code null} if it starts with an
     * argument ({@code %}), an optional group ({@code [}), or an alternation ({@code (}).
     */
    static String leadingLiteral(String pattern) {
        int i = 0;
        while (i < pattern.length() && Character.isWhitespace(pattern.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < pattern.length()
                && (Character.isLetterOrDigit(pattern.charAt(i)) || pattern.charAt(i) == '_')) {
            i++;
        }
        // If the first word is immediately followed (no space) by an optional group or alternation,
        // its spelling varies (e.g. `ender[ ]chest`, `fall[en]`, `x[-coordinate]`), so it can't be a
        // reliable index key — treat it as a wildcard so it is always a parse candidate.
        if (i < pattern.length() && (pattern.charAt(i) == '[' || pattern.charAt(i) == '(')) {
            return null;
        }
        return i > start ? pattern.substring(start, i).toLowerCase(java.util.Locale.ROOT) : null;
    }

    private static boolean endsWith(StringBuilder sb, String suffix) {
        int len = sb.length();
        int sufLen = suffix.length();
        if (len < sufLen) {
            return false;
        }
        return sb.substring(len - sufLen).equals(suffix);
    }
}
