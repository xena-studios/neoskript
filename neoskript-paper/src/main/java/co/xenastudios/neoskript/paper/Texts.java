/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Update;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Working with text: case, length, joining and splitting, parts of text, matching and replacing. */
final class Texts {

    private Texts() {}

    static void register(Registrar registrar) {
        // 'concat("a", 1)' joins anything as text; 'case_equals(...)' compares texts with case.
        registrar.function("concat", List.of(new Parameter("texts", "object", true, false)), "string", false, args -> {
            StringBuilder joined = new StringBuilder();
            for (Object value : (Object[]) args[0]) {
                joined.append(Values.text(value));
            }
            return joined.toString();
        });
        registrar.function("case_equals", List.of(new Parameter("strs", "string", true, false)), "boolean", false, args -> {
            Object[] texts = (Object[]) args[0];
            for (int i = 1; i < texts.length; i++) {
                if (!texts[i].equals(texts[0])) {
                    return false;
                }
            }
            return true;
        });
        registrar.expression("ExprStringCase", "string", info -> {
            UnaryOperator<String> change = switch (info.pattern()) {
                case 0, 1 -> info.hasTag("upper") ? s -> s.toUpperCase(Locale.ROOT) : s -> s.toLowerCase(Locale.ROOT);
                case 2 -> Texts::capitalize;
                default -> {
                    boolean strict = info.hasTag("strict");
                    yield s -> titleCase(s, strict);
                }
            };
            return (context, args) -> map((Object[]) args[0], change);
        }, "%texts% in (upper:upper|lower)[ ]case", "(upper:upper|lower)[ ]case %texts%", "capitali(s|z)ed %texts%",
                "%texts% in [(lenient|strict:strict) ](proper|title)[ ]case",
                "[(lenient|strict:strict) ](proper|title)[ ]case %texts%");

        registrar.expression("ExprLength", "integer", info -> (context, args) -> {
            Object[] texts = (Object[]) args[0];
            Object[] lengths = new Object[texts.length];
            for (int i = 0; i < texts.length; i++) {
                lengths[i] = (long) Values.text(texts[i]).length();
            }
            return lengths.length == 1 ? lengths[0] : lengths;
        }, "[the] length of %texts%", "%texts%'[s] length");

        registrar.expression("ExprJoinSplit", "string", info -> {
            int pattern = info.pattern();
            boolean caseSensitive = info.hasTag("case");
            boolean dropTrailing = info.hasTag("trailing");
            return (context, args) -> {
                if (pattern == 0) {
                    List<String> parts = new ArrayList<>();
                    for (Object text : (Object[]) args[0]) {
                        parts.add(Values.text(text));
                    }
                    return String.join(args[1] == null ? "" : Values.text(args[1]), parts);
                }
                String text = Values.text(args[0]);
                String delimiter = Values.text(args[1]);
                String regex = pattern >= 3 ? delimiter
                        : (caseSensitive ? "" : "(?iu)") + Pattern.quote(delimiter);
                try {
                    String[] parts = text.split(regex, -1);
                    if (dropTrailing && parts.length > 0 && parts[parts.length - 1].isEmpty()) {
                        return java.util.Arrays.copyOf(parts, parts.length - 1);
                    }
                    return parts;
                } catch (PatternSyntaxException e) {
                    return new Object[0];
                }
            };
        }, "(concat[enate]|join) %texts% [(with|using|by) [[the] delimiter] %text%]",
                "split %text% (at|using|by) [[the] delimiter] %text% [case:with case sensitivity] "
                        + "[trailing:without [the] trailing [empty] (string|text)]",
                "%text% split (at|using|by) [[the] delimiter] %text% [case:with case sensitivity] "
                        + "[trailing:without [the] trailing [empty] (string|text)]",
                "regex split %text% (at|using|by) [[the] delimiter] %text% [trailing:without [the] trailing [empty] (string|text)]",
                "regex %text% split (at|using|by) [[the] delimiter] %text% [trailing:without [the] trailing [empty] (string|text)]");

        registrar.expression("ExprSubstring", "string", info -> {
            int pattern = info.pattern();
            boolean last = info.hasTag("last");
            return (context, args) -> switch (pattern) {
                case 0 -> map((Object[]) args[0], s -> part(s, number(args[1], 1), number(args[2], 1)));
                case 1, 2 -> {
                    int count = number(pattern == 1 ? args[0] : args[0], 1);
                    yield map((Object[]) args[1], s -> last
                            ? s.substring(Math.max(0, s.length() - Math.max(count, 0)))
                            : s.substring(0, Math.min(s.length(), Math.max(count, 0))));
                }
                default -> {
                    List<Object> characters = new ArrayList<>();
                    for (Object text : (Object[]) args[1]) {
                        String s = Values.text(text);
                        for (Object index : (Object[]) args[0]) {
                            int i = number(index, 0) - 1;
                            if (i >= 0 && i < s.length()) {
                                characters.add(String.valueOf(s.charAt(i)));
                            }
                        }
                    }
                    yield characters.toArray();
                }
            };
        }, "[the] (part|sub[ ](text|string)) of %texts% (between|from) [ind(ex|ices)|character[s]] %number% (and|to) [(index|character)] %number%",
                "[the] (first|last:last) [%number%] character[s] of %texts%",
                "[the] %number% (first|last:last) characters of %texts%",
                "[the] character[s] at [(index|position|indexes|indices|positions)] %numbers% (in|of) %texts%");

        registrar.expression("ExprNumberOfCharacters", "integer", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                String text = Values.text(args[0]);
                long count = text.codePoints().filter(c -> switch (pattern) {
                    case 0 -> Character.isUpperCase(c);
                    case 1 -> Character.isLowerCase(c);
                    default -> Character.isDigit(c);
                }).count();
                return count;
            };
        }, "number of upper[ ]case char(acters|s) in %text%", "number of lower[ ]case char(acters|s) in %text%",
                "number of digit char(acters|s) in %text%");

        registrar.condition("CondMatches", info -> {
            boolean negated = info.hasTag("not");
            boolean partial = info.pattern() == 1;
            boolean textsAnd = info.and(0);
            boolean regexesAnd = info.and(1);
            return (context, args) -> negated != Condition.check((Object[]) args[0], textsAnd, text ->
                    Condition.check((Object[]) args[1], regexesAnd, regex -> {
                        try {
                            Matcher matcher = Pattern.compile(Values.text(regex)).matcher(Values.text(text));
                            return partial ? matcher.find() : matcher.matches();
                        } catch (PatternSyntaxException e) {
                            return false;
                        }
                    }));
        }, "%texts% (match[es]|not:do[es](n't| not) match) %texts%",
                "%texts% (partially match[es]|not:do[es](n't| not) partially match) %texts%");

        registrar.effect("EffReplace", info -> {
            int pattern = info.pattern();
            boolean regex = pattern >= 2;
            boolean first = info.hasTag("first");
            boolean caseSensitive = info.hasTag("case");
            // 'replace X in T with R' or 'replace X with R in T'.
            boolean targetLast = pattern % 2 == 1;
            return Update.of(targetLast ? 2 : 1, (context, args) -> {
                Object[] needles = (Object[]) args[0];
                Object[] targets = (Object[]) args[targetLast ? 2 : 1];
                String replacement = Values.text(args[targetLast ? 1 : 2]);
                Object[] result = new Object[targets.length];
                for (int i = 0; i < targets.length; i++) {
                    String text = Values.text(targets[i]);
                    for (Object needle : needles) {
                        String find = Values.text(needle);
                        String pattern1 = regex ? find : (caseSensitive ? "" : "(?iu)") + Pattern.quote(find);
                        String with = regex ? replacement : Matcher.quoteReplacement(replacement);
                        try {
                            text = first ? text.replaceFirst(pattern1, with) : text.replaceAll(pattern1, with);
                        } catch (RuntimeException e) {
                            // An invalid regex replaces nothing.
                        }
                    }
                    result[i] = text;
                }
                return result;
            });
        }, "replace [(all|every)|[the] first:first] %texts% in %texts% with %text% [case:with case sensitivity]",
                "replace [(all|every)|[the] first:first] %texts% with %text% in %texts% [case:with case sensitivity]",
                "(replace [with|using] regex|regex replace) %texts% in %texts% with %text%",
                "(replace [with|using] regex|regex replace) %texts% with %text% in %texts%");

        registrar.expression("LitNewLine", "string", info -> (context, args) -> "\n", "nl", "new[ ]line", "line[ ]break");

        registrar.expression("ExprColored", "string", info -> {
            boolean strip = info.hasTag("un");
            return (context, args) -> map((Object[]) args[0], s -> strip
                    ? Messages.LEGACY_SECTION.serialize(Messages.component(s)).replaceAll("§[0-9a-fk-orx]", "")
                    : Messages.LEGACY_SECTION.serialize(Messages.component(s)));
        }, "[un:(un|non)[-]](colo[u]r-|colo[u]red )%texts%", "[un:(un|non)[-]](format-|formatted )%texts%");
    }

    private static Object map(Object[] texts, UnaryOperator<String> change) {
        Object[] result = new Object[texts.length];
        for (int i = 0; i < texts.length; i++) {
            result[i] = change.apply(Values.text(texts[i]));
        }
        return result.length == 1 ? result[0] : result;
    }

    private static int number(Object value, int otherwise) {
        return value instanceof Number number ? (int) Math.round(number.doubleValue()) : otherwise;
    }

    /** Characters {@code from} to {@code to} of {@code text}, counting from 1, both included. */
    private static String part(String text, int from, int to) {
        int start = Math.max(from, 1) - 1;
        int end = Math.min(to, text.length());
        return start >= end ? "" : text.substring(start, end);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    /** Each word with a capital first letter; strict title case also makes the rest lower case. */
    private static String titleCase(String text, boolean strict) {
        StringBuilder result = new StringBuilder(text.length());
        boolean start = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                start = true;
                result.append(c);
            } else if (start) {
                result.append(Character.toUpperCase(c));
                start = false;
            } else {
                result.append(strict ? Character.toLowerCase(c) : c);
            }
        }
        return result.toString();
    }
}
