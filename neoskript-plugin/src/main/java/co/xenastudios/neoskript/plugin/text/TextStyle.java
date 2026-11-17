/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.text;

import co.xenastudios.neoskript.plugin.config.Codec;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * A colour and decorations written as MiniMessage tags, such as {@code <gray>} or {@code <#FF4D9D><bold>}.
 * Bare names work too: {@code gold bold}.
 */
public final class TextStyle {

    public static final TextStyle NONE = new TextStyle("", Style.empty());

    // Stands in for the text the tags would style, to find the style they give it.
    private static final String MARK = "";

    public static final Codec<TextStyle> CODEC = new Codec<>() {
        @Override
        public TextStyle decode(Object raw) {
            return parse(raw.toString());
        }

        @Override
        public Object encode(TextStyle value) {
            return value.tags;
        }
    };

    private final String tags;
    private final Style style;

    private TextStyle(String tags, Style style) {
        this.tags = tags;
        this.style = style;
    }

    /** What the config says. */
    public String tags() {
        return tags;
    }

    /** What the tags mean. */
    public Style style() {
        return style;
    }

    /**
     * @throws IllegalArgumentException if the tags aren't only colours and decorations
     */
    public static TextStyle parse(String tags) {
        String trimmed = tags.trim();
        if (trimmed.isEmpty()) {
            return NONE;
        }
        String mini = trimmed.startsWith("<") ? trimmed : "<" + String.join("><", trimmed.split("\\s+")) + ">";
        // Two marks: a gradient or rainbow would colour them differently.
        Component parsed = MiniMessage.miniMessage().deserialize(mini + MARK + MARK);
        // Anything MiniMessage didn't recognise is left as text beside the marks.
        if (!PlainTextComponentSerializer.plainText().serialize(parsed).equals(MARK + MARK)) {
            throw new IllegalArgumentException("isn't a colour or style, such as <gray> or <#FF4D9D><bold>");
        }
        List<Style> styles = new ArrayList<>();
        collectMarkStyles(parsed, Style.empty(), styles);
        if (styles.stream().distinct().count() > 1) {
            throw new IllegalArgumentException("can't be a gradient or rainbow; put those in the prefix instead");
        }
        Style style = styles.isEmpty() ? Style.empty() : styles.getFirst();
        if (style.clickEvent() != null || style.hoverEvent() != null || style.insertion() != null) {
            throw new IllegalArgumentException("can only be colours and decorations, not clicks or hovers");
        }
        return new TextStyle(trimmed, style);
    }

    // The style each mark character ends up with.
    private static void collectMarkStyles(Component component, Style inherited, List<Style> styles) {
        Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text) {
            text.content().chars().filter(c -> c == MARK.charAt(0)).forEach(c -> styles.add(style));
        }
        for (Component child : component.children()) {
            collectMarkStyles(child, style, styles);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TextStyle that && tags.equals(that.tags);
    }

    @Override
    public int hashCode() {
        return tags.hashCode();
    }

    @Override
    public String toString() {
        return tags;
    }
}
