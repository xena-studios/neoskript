/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * A MiniMessage message that can use the {@link Theme}'s tags. Parsed the first time it's used and kept until
 * the theme changes; parsed again only when it's sent with placeholders. A blank message sends nothing.
 */
public final class Message {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Message EMPTY = new Message("");

    private static volatile Theme.Compiled theme = Theme.DEFAULTS.compile();

    private final String template;
    private volatile Parsed parsed;

    private Message(String template) {
        this.template = template;
    }

    public static Message of(String template) {
        return template.isBlank() ? EMPTY : new Message(template);
    }

    /** Sets the theme every message renders with. */
    public static void theme(Theme theme) {
        Message.theme = theme.compile();
    }

    public String template() {
        return template;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    /** The message as a component, with these placeholders filled in. */
    public Component render(TagResolver... placeholders) {
        if (isEmpty()) {
            return Component.empty();
        }
        Theme.Compiled current = theme;
        if (placeholders.length > 0) {
            return MINI_MESSAGE.deserialize(template, TagResolver.resolver(current.tags(), TagResolver.resolver(placeholders)));
        }
        Parsed cached = parsed;
        if (cached == null || cached.theme != current) {
            cached = new Parsed(current, MINI_MESSAGE.deserialize(template, current.tags()));
            parsed = cached;
        }
        return cached.component;
    }

    /** Sends the message in the theme's base colour, unless it's blank. */
    public void send(Audience audience, TagResolver... placeholders) {
        if (!isEmpty()) {
            audience.sendMessage(chat(placeholders));
        }
    }

    /** The message as sent in chat: in the theme's base colour where it sets none. */
    public Component chat(TagResolver... placeholders) {
        return base(render(placeholders));
    }

    /** {@code component} in the theme's base colour where it sets none. */
    public static Component base(Component component) {
        Theme.Compiled current = theme;
        return current.base().isEmpty() ? component : Component.text().style(current.base()).append(component).build();
    }

    /** The message as plain text, as the console shows it. */
    public String plain(TagResolver... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(render(placeholders));
    }

    /** A placeholder for text, which is shown as it is: tags in it aren't read. */
    public static TagResolver text(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** A placeholder for a component. */
    public static TagResolver component(String name, Component value) {
        return Placeholder.component(name, value);
    }

    private record Parsed(Theme.Compiled theme, Component component) {}

    @Override
    public boolean equals(Object other) {
        return other instanceof Message message && template.equals(message.template);
    }

    @Override
    public int hashCode() {
        return template.hashCode();
    }

    @Override
    public String toString() {
        return template;
    }
}
