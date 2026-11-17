/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.text;

import co.xenastudios.neoskript.plugin.config.Comment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * The look every message shares: a prefix and four styles, used in messages as tags, as in Xena Studios'
 * other plugins. Changing one here restyles every message that uses it.
 */
public record Theme(
        @Comment({
            "<prefix> starts NeoSkript's messages, such as \"<dark_gray>[<#FF4D9D>MyServer</#FF4D9D>]</dark_gray> \",",
            "or blank for none."})
        String prefix,
        @Comment("The colour of message text that has no tag of its own.") TextStyle base,
        @Comment("<highlight>: names and values, such as a script or a number.") TextStyle highlight,
        @Comment("<success>: something done, such as a script reloaded.") TextStyle success,
        @Comment("<error>: something that went wrong, such as a script's errors.") TextStyle error) {

    /** NeoSkript's own colours: its magenta, #FF4D9D, for the prefix and highlights. */
    public static final Theme DEFAULTS = new Theme(
            "<dark_gray>[<#FF4D9D>NeoSkript</#FF4D9D>]</dark_gray> ",
            TextStyle.parse("<gray>"),
            TextStyle.parse("<#FF4D9D>"),
            TextStyle.parse("<#5EE08A>"),
            TextStyle.parse("<#FF6B6B>"));

    /** The theme as MiniMessage tags, ready to render with. */
    Compiled compile() {
        TagResolver styles = TagResolver.resolver(
                style("highlight", highlight),
                style("success", success),
                style("error", error));
        Component prefixed = prefix.isEmpty() ? Component.empty() : MiniMessage.miniMessage().deserialize(prefix, styles);
        return new Compiled(TagResolver.resolver(styles, Placeholder.component("prefix", prefixed)), base.style());
    }

    private static TagResolver style(String name, TextStyle style) {
        return TagResolver.resolver(name, Tag.styling(builder -> builder.merge(style.style())));
    }

    record Compiled(TagResolver tags, Style base) {}
}
