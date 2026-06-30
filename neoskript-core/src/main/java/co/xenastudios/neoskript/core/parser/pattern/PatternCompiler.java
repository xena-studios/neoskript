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
        int i = 0;
        int length = pattern.length();

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
                    i = end + 1;
                }
                case '[' -> {
                    // Pull a preceding whitespace matcher inside the optional group so that the
                    // surrounding space is also optional when the group is absent.
                    if (endsWith(regex, "\\s+")) {
                        regex.setLength(regex.length() - 3);
                        regex.append("(?:\\s+");
                    } else {
                        regex.append("(?:");
                    }
                    i++;
                }
                case ']' -> {
                    regex.append(")?");
                    i++;
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
                argCount, leadingLiteral(pattern));
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
