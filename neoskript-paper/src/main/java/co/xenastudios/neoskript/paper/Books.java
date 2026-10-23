/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.BookMeta;

/** Written books: their title, author and pages. */
final class Books {

    private Books() {}

    static void register(Registrar registrar) {
        registrar.expression("ExprBookTitle", "string", info -> new SettableProperty<>(ItemType.class, String.class,
                item -> item.stack().getItemMeta() instanceof BookMeta book && book.title() != null
                        ? Messages.LEGACY_SECTION.serialize(book.title()) : null,
                (item, title) -> item.editMeta(meta -> {
                    if (meta instanceof BookMeta book) {
                        book.title(Messages.component(title));
                    }
                })), "[the] book (title|name) of %itemtypes%", "%itemtypes%'[s] book (title|name)");
        registrar.expression("ExprBookAuthor", "string", info -> new SettableProperty<>(ItemType.class, String.class,
                item -> item.stack().getItemMeta() instanceof BookMeta book && book.author() != null
                        ? Messages.LEGACY_SECTION.serialize(book.author()) : null,
                (item, author) -> item.editMeta(meta -> {
                    if (meta instanceof BookMeta book) {
                        book.author(Messages.component(author));
                    }
                })), "[the] [book] (author|writer|publisher) of %itemtypes%", "%itemtypes%'[s] [book] (author|writer|publisher)");
        registrar.expression("ExprBookPages", "string", info -> new Pages(info.pattern() >= 2),
                "[the] [book] (pages|content[s]) of %itemtypes%", "%itemtypes%'[s] [book] (pages|content[s])",
                "[the] [book] page %number% of %itemtypes%", "%itemtypes%'[s] [book] page %number%");
    }

    /** {@code the pages of %books%}, or with {@code single}, {@code page %number% of %books%}. */
    private record Pages(boolean single) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> pages = new ArrayList<>();
            int number = single && arguments[0] instanceof Number n ? n.intValue() : 0;
            for (Object value : items(arguments)) {
                if (value instanceof ItemType item && item.stack().getItemMeta() instanceof BookMeta book) {
                    List<Component> all = book.pages();
                    if (!single) {
                        all.forEach(page -> pages.add(Messages.LEGACY_SECTION.serialize(page)));
                    } else if (number >= 1 && number <= all.size()) {
                        pages.add(Messages.LEGACY_SECTION.serialize(all.get(number - 1)));
                    }
                }
            }
            return pages.size() == 1 ? pages.getFirst() : pages.toArray();
        }

        private Object[] items(Object[] arguments) {
            Object items = arguments[single ? 1 : 0];
            return items instanceof Object[] array ? array : new Object[] {items};
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {String.class};
                case ADD -> single ? null : new Class<?>[] {String.class};
                case DELETE, RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            int number = single && arguments[0] instanceof Number n ? n.intValue() : 0;
            List<Component> given = new ArrayList<>();
            for (Object value : delta == null ? new Object[0] : delta) {
                given.add(Messages.component(Values.text(value)));
            }
            for (Object value : items(arguments)) {
                if (!(value instanceof ItemType item)) {
                    continue;
                }
                item.editMeta(meta -> {
                    if (!(meta instanceof BookMeta book)) {
                        return;
                    }
                    List<Component> pages = new ArrayList<>(book.pages());
                    if (!single) {
                        switch (mode) {
                            case SET -> pages = given;
                            case ADD -> pages.addAll(given);
                            default -> pages.clear();
                        }
                    } else if (number >= 1) {
                        // Pages up to it are made, empty, as needed.
                        while (pages.size() < number && mode == Mode.SET) {
                            pages.add(Component.empty());
                        }
                        if (number <= pages.size()) {
                            switch (mode) {
                                case SET -> pages.set(number - 1, given.isEmpty() ? Component.empty() : given.getFirst());
                                case DELETE -> pages.remove(number - 1);
                                default -> pages.set(number - 1, Component.empty());
                            }
                        }
                    }
                    book.pages(pages);
                });
            }
        }
    }
}
