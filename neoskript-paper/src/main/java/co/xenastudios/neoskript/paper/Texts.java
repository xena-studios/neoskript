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
            // A regular expression written out is checked while parsing.
            String written = pattern >= 3 ? info.argumentLiteral(1) : null;
            if (written != null) {
                try {
                    Pattern.compile(written);
                } catch (PatternSyntaxException invalid) {
                    throw new co.xenastudios.neoskript.api.element.SyntaxError(
                            "'" + written + "' is not a valid regular expression");
                }
            }
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
                Object by = args[targetLast ? 1 : 2];
                if (by == null) {
                    // Replacing with nothing changes nothing.
                    return targets.clone();
                }
                String replacement = Values.text(by);
                Object[] result = new Object[targets.length];
                for (int i = 0; i < targets.length; i++) {
                    // An inventory has its items replaced, in place: 'replace all dirt in {_inv} with bone'.
                    if (targets[i] instanceof org.bukkit.inventory.Inventory inventory) {
                        if (by instanceof ItemType item) {
                            replaceItems(inventory, needles, item);
                        }
                        result[i] = inventory;
                        continue;
                    }
                    if (!(targets[i] instanceof String)) {
                        // Neither text nor an inventory: left as it is.
                        result[i] = targets[i];
                        continue;
                    }
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
        }, "replace [(all|every)|first:[the] first] %texts/itemtypes% in %texts/inventories% with %text/itemtype% [case:with case sensitivity]",
                "replace [(all|every)|first:[the] first] %texts/itemtypes% with %text/itemtype% in %texts/inventories% [case:with case sensitivity]",
                "(replace [with|using] regex|regex replace) %texts% in %texts% with %text%",
                "(replace [with|using] regex|regex replace) %texts% with %text% in %texts%");

        registrar.expression("ExprRepeat", "string", info -> (context, args) -> {
            if (!(args[1] instanceof Number times) || times.doubleValue() < 1) {
                return ((Object[]) args[0]).length == 0 || !(args[1] instanceof Number) ? null : new Object[0];
            }
            int count = (int) Math.min(times.longValue(), Integer.MAX_VALUE);
            return map((Object[]) args[0], text -> text.repeat(count));
        }, "%texts% repeated %integer% time[s]");
        registrar.expression("ExprCodepoint", "integer", info -> (context, args) -> {
            List<Object> codes = new java.util.ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof String text && !text.isEmpty()) {
                    codes.add((long) text.charAt(0));
                }
            }
            return codes.size() == 1 ? codes.getFirst() : codes.toArray();
        }, "[the] [unicode|character] code([ ]point| position) of %texts%", "%texts%'[s] [unicode|character] code([ ]point| position)");
        registrar.expression("ExprCharacterFromCodepoint", "string", info -> (context, args) -> {
            List<Object> characters = new java.util.ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Number code) {
                    // As a Java char: wrapping round, with NaN as 0 and infinity as the last one.
                    double d = code.doubleValue();
                    characters.add(String.valueOf((char) (Double.isNaN(d) ? 0 : d >= Integer.MAX_VALUE ? 65535 : (int) d)));
                }
            }
            return characters.size() == 1 ? characters.getFirst() : characters.toArray();
        }, "character (from|at|with) code([ ]point| position) %numbers%");
        registrar.expression("ExprCharacters", "string", info -> {
            boolean alphanumeric = info.hasTag("alphanumeric");
            return (context, args) -> {
                if (!(args[0] instanceof String from) || !(args[1] instanceof String to) || from.isEmpty() || to.isEmpty()) {
                    return null;
                }
                char start = from.charAt(0);
                char end = to.charAt(0);
                int step = start <= end ? 1 : -1;
                List<Object> characters = new java.util.ArrayList<>();
                for (int c = start; step > 0 ? c <= end : c >= end; c += step) {
                    if (!alphanumeric || Character.isLetterOrDigit(c)) {
                        characters.add(String.valueOf((char) c));
                    }
                }
                return characters.toArray();
            };
        }, "[(all [[of] the]|the)] [alphanumeric:alphanumeric] characters (between|from) %text% (and|to) %text%");
        registrar.expression("ExprRandomCharacter", "string", info -> {
            boolean alphanumeric = info.hasTag("alphanumeric");
            boolean counted = info.hasTag("count");
            return (context, args) -> {
                // An amount that's nothing gives nothing; one left out is 1.
                if (counted && !(args[0] instanceof Number)) {
                    return null;
                }
                if (!(args[1] instanceof String from) || !(args[2] instanceof String to) || from.isEmpty() || to.isEmpty()) {
                    return null;
                }
                char low = (char) Math.min(from.charAt(0), to.charAt(0));
                char high = (char) Math.max(from.charAt(0), to.charAt(0));
                List<Character> choices = new java.util.ArrayList<>();
                for (char c = low; c <= high && c >= low; c++) {
                    if (!alphanumeric || Character.isLetterOrDigit(c)) {
                        choices.add(c);
                    }
                    if (c == Character.MAX_VALUE) {
                        break;
                    }
                }
                int count = args[0] instanceof Number n ? (int) Math.max(0, n.longValue()) : 1;
                if (choices.isEmpty()) {
                    return null;
                }
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                Object[] picked = new Object[count];
                for (int i = 0; i < count; i++) {
                    picked[i] = String.valueOf(choices.get(random.nextInt(choices.size())));
                }
                return picked.length == 1 ? picked[0] : picked;
            };
        }, "[a|count:%integer%] random [alphanumeric:alphanumeric] character[s] (from|between) %text% (to|and) %text%");
        registrar.expression("ExprStringCase", "string", info -> {
            String style = info.tags().stream().filter(tag -> tag.startsWith("s_")).findFirst().orElse("s_upper");
            boolean strict = info.hasTag("strict");
            String variant = info.hasTag("lower") ? "lower" : info.hasTag("upper") ? "upper" : "";
            return (context, args) -> map((Object[]) args[0], text -> textCase(text, style, strict, variant));
        }, "%texts% in (s_upper:(upper|caps)[ ]case|s_lower:lower[ ]case)",
                "(s_upper:capitali(s|z)ed|s_upper:upper[ ]case[d]|s_lower:lower[ ]case[d]) %texts%",
                "%texts% in [(lenient|strict:strict) ](s_proper:(proper|title)[ ]case|s_camel:camel[ ]case|s_pascal:pascal[ ]case)",
                "[(lenient|strict:strict) ](s_proper:(proper|title)[ ]case|s_camel:camel[ ]case|s_pascal:pascal[ ]case) %texts%",
                "%texts% in [(lower:lower|upper:upper) ](s_snake:snake[ ]case|s_kebab:kebab[ ]case)",
                "[(lower:lower|upper:upper) ](s_snake:snake[ ]case|s_kebab:kebab[ ]case) %texts%");
        registrar.expression("LitNewLine", "string", info -> (context, args) -> "\n", "nl", "new[ ]line", "line[ ]break");

        registrar.expression("ExprColored", "string", info -> {
            boolean strip = info.hasTag("un");
            return (context, args) -> map((Object[]) args[0], s -> strip
                    ? Messages.plain(s) : Messages.LEGACY_SECTION.serialize(Messages.component(s)));
        }, "[un:(un|non)[-]](colo[u]r-|colo[u]red )%texts%", "[un:(un|non)[-]](format-|formatted )%texts%");
    }

    /** {@code text} in one of Skript's cases: proper, camel, pascal, snake or kebab, upper or lower. */
    private static String textCase(String text, String style, boolean strict, String variant) {
        String[] words = text.split(" ", -1);
        StringBuilder out = new StringBuilder();
        switch (style) {
            case "s_lower" -> {
                return text.toLowerCase(java.util.Locale.ROOT);
            }
            case "s_proper", "s_camel", "s_pascal" -> {
                for (int i = 0; i < words.length; i++) {
                    String word = strict ? words[i].toLowerCase(java.util.Locale.ROOT) : words[i];
                    boolean capital = !style.equals("s_camel") || i > 0;
                    if (capital && !word.isEmpty()) {
                        word = word.substring(0, word.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT)
                                + word.substring(word.offsetByCodePoints(0, 1));
                    }
                    if (style.equals("s_proper") && i > 0) {
                        out.append(' ');
                    }
                    out.append(word);
                }
                return out.toString();
            }
            case "s_snake", "s_kebab" -> {
                String joined = String.join(style.equals("s_snake") ? "_" : "-", words);
                return switch (variant) {
                    case "lower" -> joined.toLowerCase(java.util.Locale.ROOT);
                    case "upper" -> joined.toUpperCase(java.util.Locale.ROOT);
                    default -> joined;
                };
            }
            default -> {
                return text.toUpperCase(java.util.Locale.ROOT);
            }
        }
    }

    /** Each stack of {@code needles} in {@code inventory} becomes {@code replacement}, keeping its size. */
    private static void replaceItems(org.bukkit.inventory.Inventory inventory, Object[] needles, ItemType replacement) {
        org.bukkit.inventory.ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            org.bukkit.inventory.ItemStack held = contents[slot];
            if (held == null) {
                continue;
            }
            for (Object wanted : needles) {
                if (wanted instanceof ItemType type && type.material() == held.getType()
                        && (!type.stack().hasItemMeta() || type.stack().isSimilar(held))) {
                    org.bukkit.inventory.ItemStack replaced = replacement.stack();
                    replaced.setAmount(held.getAmount());
                    inventory.setItem(slot, replaced);
                    break;
                }
            }
        }
    }

    private static Object map(Object[] texts, UnaryOperator<String> change) {
        Object[] result = new Object[texts.length];
        for (int i = 0; i < texts.length; i++) {
            result[i] = change.apply(Values.text(texts[i]));
        }
        return result.length == 1 ? result[0] : result;
    }

    private static int number(Object value, int otherwise) {
        return value instanceof Number number ? co.xenastudios.neoskript.runtime.Values.toInt(number.doubleValue()) : otherwise;
    }

    /** Characters {@code from} to {@code to} of {@code text}, counting from 1, both included. */
    private static String part(String text, int from, int to) {
        int start = Math.max(from, 1) - 1;
        int end = Math.min(to, text.length());
        return start >= end ? "" : text.substring(start, end);
    }
}
