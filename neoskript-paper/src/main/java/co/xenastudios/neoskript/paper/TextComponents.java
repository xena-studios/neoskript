/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import co.xenastudios.neoskript.runtime.Values;
import io.papermc.paper.text.PaperComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

/**
 * Skript's text module: the text component type, how text and components turn into each other, compare and
 * join, and {@code colored}/{@code formatted}, {@code raw} and {@code resolved for}. Messages themselves are
 * sent by {@link Messages}.
 */
final class TextComponents {

    private TextComponents() {}

    private static final TextComponentParser PARSER = TextComponentParser.instance();

    static void register(Registrar registrar) {
        registrar.type("textcomponent", "text component", Component.class);
        registrar.text(Component.class, PARSER::toString);
        registrar.variableNameText(Component.class, PARSER::toString);
        registrar.converter(String.class, Component.class, PARSER::parseSafe);
        // As a conversion, legacy formatting is what's probably wanted.
        registrar.converter(Component.class, String.class, PARSER::toLegacyString);
        // Components can hold virtual parts that don't compare, so they're compared as they're written.
        registrar.comparison(Component.class, String.class, (component, text) ->
                PARSER.toString(component).equalsIgnoreCase(PARSER.toString(PARSER.parse(text))));
        registrar.comparison(Component.class, Component.class, (a, b) ->
                PARSER.toString(a).equalsIgnoreCase(PARSER.toString(b)));
        registrar.arithmetic('+', Component.class, Component.class, TextComponents::appendToEnd);
        registrar.arithmetic('+', Component.class, String.class,
                (component, text) -> appendToEnd(component, PARSER.parseSafe(text)));
        registrar.arithmetic('+', String.class, Component.class,
                (text, component) -> appendToEnd(PARSER.parseSafe(text), component));
        registrar.storage("textcomponent", Component.class, new VariableCodec<>() {
            @Override
            public void encode(Component component, BinaryWriter out) {
                out.beginObject(1);
                out.stringField("json", JSONComponentSerializer.json().serialize(component));
            }

            @Override
            public Component decode(BinaryReader in) {
                in.beginObject();
                return JSONComponentSerializer.json().deserialize(in.stringField("json"));
            }
        });

        // 'colored X' reads the safe tags, 'formatted X' all of them; 'uncolored' and 'unformatted' take them out.
        registrar.expression("ExprColored", "textcomponent", info -> {
            boolean format = info.pattern() == 1;
            return (context, args) -> map((Object[]) args[0], text -> format ? PARSER.parse(text) : PARSER.parseSafe(text));
        }, "(colo[u]r-|colo[u]red )%strings%", "(format-|formatted )%strings%");
        registrar.expression("ExprColored-un", "string", info -> {
            boolean format = info.pattern() == 1;
            return (context, args) -> map((Object[]) args[0], text -> format
                    ? PARSER.stripFormatting(text) : PARSER.stripSafeFormatting(text));
        }, "(un|non)[-](colo[u]r-|colo[u]red )%strings%", "(un|non)[-](format-|formatted )%strings%");

        // 'raw X': the text as it is, tags and all.
        registrar.expression("ExprRawString", "textcomponent", info -> (context, args) ->
                map((Object[]) args[0], Component::text), "raw %strings%");

        // 'X resolved for player': selectors, scores and NBT worked out as the sender sees them.
        registrar.expression("ExprResolvedComponent", "textcomponent", info -> {
            boolean bypass = info.hasTag("bypass");
            Component literal = literal(info, 0);
            return (context, args) -> {
                if (!(Values.single(args[1]) instanceof CommandSender sender)) {
                    return new Object[0];
                }
                Entity subject = sender instanceof Entity entity ? entity : null;
                // Something that isn't a player, say a pig, needs no permissions.
                boolean bypassing = bypass || !(sender instanceof OfflinePlayer);
                Object[] components = literal != null ? new Object[] {literal} : Values.array(args[0]);
                List<Object> resolved = new ArrayList<>();
                for (Object value : components) {
                    if (!(value instanceof Component component)) {
                        continue;
                    }
                    try {
                        resolved.add(PaperComponents.resolveWithContext(component, sender, subject, bypassing));
                    } catch (java.io.IOException | RuntimeException e) {
                        // No permission, or a selector that isn't valid.
                        resolved.add(component);
                    }
                }
                return resolved.size() == 1 ? resolved.getFirst() : resolved.toArray();
            };
        }, "%textcomponents% resolved for %commandsender% [bypass:(bypassing|ignoring) required permissions]");
    }

    /**
     * Argument {@code index} as Skript makes a component of it, for messages and titles: a text written out
     * has all its tags read, other text only its safe ones, a component stays as it is, and anything else
     * is its text.
     */
    static Function<Object, Component> componentOf(ElementInfo info, int index) {
        Component literal = literal(info, index);
        if (literal != null) {
            return value -> literal;
        }
        return TextComponents::from;
    }

    /** Argument {@code index} as a component, all tags read, if it's a text written out, else {@code null}. */
    private static Component literal(ElementInfo info, int index) {
        String written = info.argumentLiteral(index);
        return written != null && "string".equals(info.argumentType(index)) ? PARSER.parse(written) : null;
    }

    /** A value as a component: a component as it is, text with its safe tags read, anything else as its text. */
    static Component from(Object message) {
        return switch (message) {
            case Component component -> component;
            case String text -> PARSER.parseSafe(text);
            default -> Component.text(Values.text(message));
        };
    }

    /** {@code appendee} added after the last of {@code base}'s parts, so it takes on their formatting. */
    static Component appendToEnd(Component base, Component appendee) {
        return appendToLastChild(base, appendee).compact();
    }

    private static Component appendToLastChild(Component base, Component appendee) {
        List<Component> children = base.children();
        if (children.isEmpty()) {
            return base.append(appendee);
        }
        children = new ArrayList<>(children);
        children.add(appendToLastChild(children.removeLast(), appendee));
        return base.children(children);
    }

    private static Object map(Object[] texts, Function<String, Object> change) {
        Object[] result = new Object[texts.length];
        for (int i = 0; i < texts.length; i++) {
            result[i] = change.apply(Values.text(texts[i]));
        }
        return result.length == 1 ? result[0] : result;
    }
}
