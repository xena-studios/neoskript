/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.bukkit.registration;

import ch.njol.skript.lang.SkriptEvent;
import java.util.Collection;
import java.util.function.Supplier;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

/** Syntax info for Bukkit events. */
public final class BukkitSyntaxInfos {

    private BukkitSyntaxInfos() {}

    /** An event's syntax info: its name and the Bukkit events it listens to. */
    public interface Event<E extends SkriptEvent> extends SyntaxInfo<E> {

        SyntaxRegistry.Key<Event<?>> KEY = SyntaxRegistry.Key.of("event");

        static <E extends SkriptEvent> Event<E> simple(Class<E> eventClass, Supplier<E> instanceSupplier,
                String name, Class<? extends org.bukkit.event.Event> event, String... patterns) {
            Builder<? extends Builder<?, E>, E> builder = builder(eventClass, name);
            builder.supplier(instanceSupplier);
            builder.addEvent(event);
            builder.addPatterns(patterns);
            return builder.build();
        }

        static <E extends SkriptEvent> Builder<? extends Builder<?, E>, E> builder(Class<E> eventClass, String name) {
            return new co.xenastudios.neoskript.compat.CompatSyntaxInfo.EventBuilder<>(eventClass, name);
        }

        @Override
        Builder<? extends Builder<?, E>, E> toBuilder();

        String name();

        Collection<Class<? extends org.bukkit.event.Event>> events();

        /** Builds an event's syntax info. */
        interface Builder<B extends Builder<B, E>, E extends SkriptEvent> extends SyntaxInfo.Builder<B, E> {

            B addEvent(Class<? extends org.bukkit.event.Event> event);

            B addEvents(Collection<Class<? extends org.bukkit.event.Event>> events);

            @Override
            Event<E> build();
        }
    }

    public static String fixPattern(String pattern) {
        return pattern;
    }
}
