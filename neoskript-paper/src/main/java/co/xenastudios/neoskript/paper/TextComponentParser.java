/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.runtime.Values;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.ParserDirective;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Text as Skript 2.17 turns it into text components (its {@code TextComponentParser}): MiniMessage tags, with
 * Skript's extra names for them ({@code <light red>}, {@code <link:...>}, {@code <unicode:00A7>}) and legacy
 * colour codes ({@code &a}, {@code &x&1&2&3&4&5&6}) read as tags.
 * <p>
 * There are two parsers. The <b>safe</b> one only reads the tags that are fine in any message, whoever wrote
 * it: colours and decorations (Skript's {@code safe tags} option). The <b>unsafe</b> one reads every tag,
 * click events included; {@code formatted} and literal texts use it. Skript's defaults for its options apply:
 * links aren't parsed, and colours reset the formatting before them.
 */
public final class TextComponentParser {

    private record SkriptTag(Tag tag, String parent, boolean reset) {}

    private record SkriptTagResolver(TagResolver resolver, String parent) {}

    /** A multi-word colour tag like {@code <dark red>}, with the backslashes before it. */
    private static final Pattern MULTI_WORD_COLOR_PATTERN = Pattern.compile("(\\\\*)<([a-zA-Z]+ [a-zA-Z]+)>");

    /** A double-hash hex tag ({@code <##123456>}), with the backslashes before it. */
    private static final Pattern LEGACY_DOUBLE_HASHTAG_PATTERN = Pattern.compile("(\\\\*)<(##[a-fA-F0-9]{6})>");

    /** A legacy code ({@code &1}), with the backslashes before it to tell whether it's escaped. */
    static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("(\\\\*)([&§][a-fA-F0-9kKlLoOmMnNrR])");

    /** A legacy hex code: {@code &x&1&2&3&4&5&6}. */
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("[&§]x(?:[&§][a-fA-F0-9]){6}");

    /** Skript's default {@code safe tags}. */
    private static final String[] DEFAULT_SAFE_TAGS = {
        "color", "decorations", "gradient", "rainbow", "reset", "transition", "pride", "shadowColor"
    };

    // After the constants it uses.
    private static final TextComponentParser INSTANCE = new TextComponentParser();

    /** The parser Skript uses everywhere. */
    public static TextComponentParser instance() {
        return INSTANCE;
    }

    private final Map<String, SkriptTag> simplePlaceholders = new HashMap<>();
    private final Set<SkriptTagResolver> resolvers = new LinkedHashSet<>();
    private TagResolver safeTagResolver = TagResolver.empty();
    private final boolean colorsCauseReset = true;

    private final MiniMessage parser = MiniMessage.builder()
            .strict(false)
            .tags(TagResolver.resolver(
                    StandardTags.defaults(),
                    // colours again, so they're written before decorations
                    StandardTags.color(),
                    skriptTagResolver(false)))
            .build();

    private final MiniMessage safeParser = MiniMessage.builder()
            .strict(false)
            .tags(TagResolver.resolver(
                    new TagResolver() {
                        @Override
                        public Tag resolve(String name, ArgumentQueue arguments, Context ctx) throws ParsingException {
                            return safeTagResolver.resolve(name, arguments, ctx);
                        }

                        @Override
                        public boolean has(String name) {
                            return safeTagResolver.has(name);
                        }
                    },
                    skriptTagResolver(true)))
            .build();

    private TextComponentParser() {
        registerCompatibilityTags();
        setSafeTags(DEFAULT_SAFE_TAGS);
    }

    /** {@code tag} with every decoration it leaves unset turned off, for a colour that resets formatting. */
    private static Tag disableUnsetStyling(Tag tag) {
        if (!(tag instanceof Inserting inserting)) {
            return tag;
        }
        Component content = inserting.value();
        Map<TextDecoration, TextDecoration.State> decorations = new HashMap<>(content.decorations());
        decorations.replaceAll((decoration, state) ->
                state == TextDecoration.State.NOT_SET ? TextDecoration.State.FALSE : state);
        content = content.decorations(decorations);
        return inserting.allowsChildren() ? Tag.inserting(content) : Tag.selfClosingInserting(content);
    }

    private TagResolver skriptTagResolver(boolean safeMode) {
        return new TagResolver() {
            @Override
            public Tag resolve(String name, ArgumentQueue arguments, Context ctx) throws ParsingException {
                SkriptTag simple = simplePlaceholders.get(name);
                if (simple != null && isSafe(name, simple.parent())) {
                    return colorsCauseReset && simple.reset() ? disableUnsetStyling(simple.tag()) : simple.tag();
                }
                for (SkriptTagResolver resolver : resolvers) {
                    if (isSafe(name, resolver.parent()) && resolver.resolver().has(name)) {
                        return resolver.resolver().resolve(name, arguments, ctx);
                    }
                }
                // Only the standard colours reset what's before them.
                if (colorsCauseReset && StandardTags.color().has(name)) {
                    Tag color = StandardTags.color().resolve(name, arguments, ctx);
                    if (color != null) {
                        return disableUnsetStyling(color);
                    }
                }
                return null;
            }

            @Override
            public boolean has(String name) {
                SkriptTag simple = simplePlaceholders.get(name);
                if (simple != null) {
                    return isSafe(name, simple.parent());
                }
                for (SkriptTagResolver resolver : resolvers) {
                    if (isSafe(name, resolver.parent()) && resolver.resolver().has(name)) {
                        return true;
                    }
                }
                return colorsCauseReset && StandardTags.color().has(name);
            }

            private boolean isSafe(String tag, String parent) {
                return !safeMode || safeTagResolver.has(tag) || (parent != null && safeTagResolver.has(parent));
            }
        };
    }

    /** Sets the tags the safe parser reads: tag names, or the names of MiniMessage's groups of them. */
    void setSafeTags(String... safeTags) {
        List<TagResolver> groups = new ArrayList<>();
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (String safeTag : safeTags) {
            try {
                MethodHandle handle = lookup.findStatic(StandardTags.class, safeTag, MethodType.methodType(TagResolver.class));
                groups.add((TagResolver) handle.invoke());
            } catch (Throwable ignored) {
                // Not a group on this version of MiniMessage: a tag name.
            }
        }
        Set<String> names = Set.of(safeTags);
        safeTagResolver = new TagResolver() {
            @Override
            public Tag resolve(String name, ArgumentQueue arguments, Context ctx) throws ParsingException {
                return has(name) ? StandardTags.defaults().resolve(name, arguments, ctx) : null;
            }

            @Override
            public boolean has(String name) {
                return names.contains(name) || groups.stream().anyMatch(group -> group.has(name));
            }
        };
    }

    private void registerPlaceholder(String name, Tag result, String parent, boolean reset) {
        simplePlaceholders.put(name, new SkriptTag(result, parent, reset));
    }

    private void registerResolver(TagResolver resolver, String parent) {
        resolvers.add(new SkriptTagResolver(resolver, parent));
    }

    /** Text as a component, every tag read. */
    public Component parse(Object message) {
        return parse(message, false);
    }

    /** Text as a component, only safe tags (colours and decorations) read. */
    public Component parseSafe(Object message) {
        return parse(message, true);
    }

    private Component parse(Object message, boolean safe) {
        String text = message instanceof String string ? string : Values.text(message);
        if (text.isEmpty()) {
            return Component.empty();
        }
        String reformatted = reformatText(text);
        try {
            return safe ? safeParser.deserialize(reformatted) : parser.deserialize(reformatted);
        } catch (ParsingException e) {
            return Component.text(text);
        }
    }

    /** Text with Skript's forms of tags and legacy codes written as MiniMessage reads them. */
    public String reformatText(String text) {
        text = MULTI_WORD_COLOR_PATTERN.matcher(text).replaceAll(result -> {
            if (result.group(1).length() % 2 == 1) {
                return Matcher.quoteReplacement(result.group());
            }
            String mapped = result.group(2).replace(" ", "_");
            if (simplePlaceholders.containsKey(mapped) || StandardTags.color().has(mapped)) {
                return Matcher.quoteReplacement(result.group(1) + "<" + mapped + ">");
            }
            return Matcher.quoteReplacement(result.group());
        });
        text = LEGACY_DOUBLE_HASHTAG_PATTERN.matcher(text).replaceAll(result -> {
            if (result.group(1).length() % 2 == 1) {
                return Matcher.quoteReplacement(result.group());
            }
            String mapped = result.group(2).substring(1);
            if (StandardTags.color().has(mapped)) {
                return Matcher.quoteReplacement("<" + mapped + ">");
            }
            return Matcher.quoteReplacement(result.group());
        });
        return replaceLegacyFormattingCodes(text);
    }

    /** Legacy codes ({@code &a}, {@code &x&1&2&3&4&5&6}) as the tags they mean. */
    static String replaceLegacyFormattingCodes(String text) {
        if (!text.contains("&") && !text.contains("§")) {
            return text;
        }
        text = LEGACY_HEX_PATTERN.matcher(text).replaceAll(result -> {
            String hex = result.group();
            StringBuilder replacement = new StringBuilder("<#");
            for (int i = 3; i <= 13; i += 2) {
                replacement.append(hex.charAt(i));
            }
            return Matcher.quoteReplacement(replacement.append('>').toString());
        });
        return LEGACY_CODE_PATTERN.matcher(text).replaceAll(result -> {
            String backslashes = result.group(1);
            if (backslashes.length() % 2 == 1) {
                // escaped
                return Matcher.quoteReplacement(result.group().substring(1));
            } else if (!backslashes.isEmpty()) {
                backslashes = backslashes.substring(1);
            }
            String tag = legacyName(Character.toLowerCase(result.group(2).charAt(1)));
            return Matcher.quoteReplacement(backslashes + "<" + tag + ">");
        });
    }

    /** The name Bukkit's colour for a legacy code has. */
    private static String legacyName(char code) {
        return switch (code) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underline";
            case 'o' -> "italic";
            default -> "reset";
        };
    }

    /** Text with every tag this parser knows escaped, and legacy codes escaped with a backslash. */
    public String escape(String string) {
        if (string.contains("&") || string.contains("§")) {
            string = LEGACY_CODE_PATTERN.matcher(string).replaceAll(result -> {
                String group = result.group().replace('§', '&');
                if (result.group(1).length() % 2 == 1) {
                    return Matcher.quoteReplacement(group);
                }
                return Matcher.quoteReplacement('\\' + group);
            });
        }
        string = LEGACY_DOUBLE_HASHTAG_PATTERN.matcher(string).replaceAll(result -> {
            if (result.group(1).length() % 2 == 1) {
                return Matcher.quoteReplacement(result.group());
            }
            String mapped = result.group(2).substring(1);
            if (StandardTags.color().has(mapped)) {
                return Matcher.quoteReplacement("\\<#" + mapped) + ">";
            }
            return Matcher.quoteReplacement(result.group());
        });
        return parser.escapeTags(string);
    }

    /** Text with all tags taken out, again and again: {@code <red<red>>} is nothing. */
    public String stripFormatting(String string) {
        return stripFormatting(string, false);
    }

    /** Text with the safe tags taken out, again and again. */
    public String stripSafeFormatting(String string) {
        return stripFormatting(string, true);
    }

    private String stripFormatting(String string, boolean onlySafe) {
        for (int i = 0; i < 64; i++) {
            String stripped = (onlySafe ? safeParser : parser).stripTags(reformatText(string));
            if (string.equals(stripped)) {
                break;
            }
            string = stripped;
        }
        return string;
    }

    /** A component's text without formatting. */
    public String stripFormatting(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** A component as tags: how Skript shows one as text. */
    public String toString(Component component) {
        return parser.serialize(component);
    }

    /** A component as legacy text, with {@code §} codes: what it is where plain text is wanted. */
    public String toLegacyString(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    private void registerCompatibilityTags() {
        registerPlaceholder("dark_cyan", Tag.styling(NamedTextColor.DARK_AQUA), "dark_aqua", true);
        registerPlaceholder("dark_turquoise", Tag.styling(NamedTextColor.DARK_AQUA), "dark_aqua", true);
        registerPlaceholder("cyan", Tag.styling(NamedTextColor.DARK_AQUA), "dark_aqua", true);

        registerPlaceholder("purple", Tag.styling(NamedTextColor.DARK_PURPLE), "dark_purple", true);

        registerPlaceholder("dark_yellow", Tag.styling(NamedTextColor.GOLD), "gold", true);
        registerPlaceholder("orange", Tag.styling(NamedTextColor.GOLD), "gold", true);

        registerPlaceholder("light_grey", Tag.styling(NamedTextColor.GRAY), "gray", true);
        registerPlaceholder("light_gray", Tag.styling(NamedTextColor.GRAY), "gray", true);
        registerPlaceholder("silver", Tag.styling(NamedTextColor.GRAY), "gray", true);

        registerPlaceholder("dark_silver", Tag.styling(NamedTextColor.DARK_GRAY), "dark_gray", true);

        registerPlaceholder("light_blue", Tag.styling(NamedTextColor.BLUE), "blue", true);
        registerPlaceholder("indigo", Tag.styling(NamedTextColor.BLUE), "blue", true);

        registerPlaceholder("light_green", Tag.styling(NamedTextColor.GREEN), "green", true);
        registerPlaceholder("lime_green", Tag.styling(NamedTextColor.GREEN), "green", true);
        registerPlaceholder("lime", Tag.styling(NamedTextColor.GREEN), "green", true);

        registerPlaceholder("light_cyan", Tag.styling(NamedTextColor.AQUA), "aqua", true);
        registerPlaceholder("light_aqua", Tag.styling(NamedTextColor.AQUA), "aqua", true);
        registerPlaceholder("turquoise", Tag.styling(NamedTextColor.AQUA), "aqua", true);

        registerPlaceholder("light_red", Tag.styling(NamedTextColor.RED), "red", true);

        registerPlaceholder("pink", Tag.styling(NamedTextColor.LIGHT_PURPLE), "light_purple", true);
        registerPlaceholder("magenta", Tag.styling(NamedTextColor.LIGHT_PURPLE), "light_purple", true);

        registerPlaceholder("light_yellow", Tag.styling(NamedTextColor.YELLOW), "yellow", true);

        // as DyeColor.BROWN
        registerPlaceholder("brown", Tag.styling(TextColor.color(0x835432)), "color", true);

        registerPlaceholder("magic", Tag.styling(TextDecoration.OBFUSCATED), "obfuscated", false);

        registerPlaceholder("strike", Tag.styling(TextDecoration.STRIKETHROUGH), "strikethrough", false);
        registerPlaceholder("s", Tag.styling(TextDecoration.STRIKETHROUGH), "strikethrough", false);

        registerPlaceholder("underline", Tag.styling(TextDecoration.UNDERLINED), "underlined", false);

        registerPlaceholder("italics", Tag.styling(TextDecoration.ITALIC), "italic", false);

        registerPlaceholder("r", ParserDirective.RESET, "reset", false);

        registerResolver(TagResolver.resolver(Set.of("open_url", "link", "url"), (arguments, context) ->
                Tag.styling(ClickEvent.openUrl(arguments.popOr("A link tag must have an argument of the url").value()))),
                "click");
        registerResolver(TagResolver.resolver(Set.of("run_command", "command", "cmd"), (arguments, context) ->
                Tag.styling(ClickEvent.runCommand(arguments
                        .popOr("A run command tag must have an argument of the command to execute").value()))), "click");
        registerResolver(TagResolver.resolver(Set.of("suggest_command", "sgt"), (arguments, context) ->
                Tag.styling(ClickEvent.suggestCommand(arguments
                        .popOr("A suggest command tag must have an argument of the command to suggest").value()))), "click");
        registerResolver(TagResolver.resolver(Set.of("change_page"), (arguments, context) -> {
            String rawPage = arguments.popOr("A change page tag must have an argument of the page number").value();
            int page;
            try {
                page = Integer.parseInt(rawPage);
            } catch (NumberFormatException e) {
                throw context.newException(e.getMessage(), arguments);
            }
            return Tag.styling(ClickEvent.changePage(page));
        }), "click");
        registerResolver(TagResolver.resolver(Set.of("copy_to_clipboard", "copy", "clipboard"), (arguments, context) ->
                Tag.styling(ClickEvent.copyToClipboard(arguments
                        .popOr("A copy to clipboard tag must have an argument of the string to copy").value()))), "click");
        registerResolver(TagResolver.resolver(Set.of("show_text", "tooltip", "ttp"), (arguments, context) ->
                Tag.styling(HoverEvent.showText(context.deserialize(arguments
                        .popOr("A tooltip tag must have an argument of the message to show").value())))), "hover");
        registerResolver(TagResolver.resolver(Set.of("f"),
                (arguments, context) -> StandardTags.font().resolve("font", arguments, context)), "font");
        registerResolver(TagResolver.resolver(Set.of("insertion", "ins"),
                (arguments, context) -> StandardTags.insertion().resolve("insert", arguments, context)), "insert");
        registerResolver(TagResolver.resolver(Set.of("keybind"),
                (arguments, context) -> StandardTags.keybind().resolve("key", arguments, context)), "key");

        Pattern unicodePattern = Pattern.compile("[0-9a-f]{4,}");
        // 'u' is MiniMessage's underline; Skript takes it for unicode
        registerResolver(TagResolver.resolver(Set.of("unicode", "u"), (arguments, context) -> {
            String argument = arguments.popOr("A unicode tag must have an argument of the unicode").value();
            Matcher matcher = unicodePattern.matcher(argument.toLowerCase(Locale.ENGLISH));
            if (!matcher.matches()) {
                throw context.newException("Invalid unicode tag");
            }
            return Tag.selfClosingInserting(Component.text(Character.toString(Integer.parseInt(matcher.group(), 16))));
        }), null);
    }
}
