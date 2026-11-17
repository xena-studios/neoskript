/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SyntaxElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SequencedCollection;
import java.util.function.Supplier;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.lang.entry.EntryValidator;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.util.Priority;

/** Skript's syntax infos, as addons build them. */
public class CompatSyntaxInfo<E extends SyntaxElement> implements SyntaxInfo<E> {

    final Class<E> type;
    final Supplier<E> supplier;
    final List<String> patterns;
    final Priority priority;
    final org.skriptlang.skript.docs.Origin origin;

    CompatSyntaxInfo(Builder<?, E> builder) {
        this.type = builder.type;
        this.supplier = builder.supplier;
        this.patterns = List.copyOf(builder.patterns);
        this.priority = builder.priority;
        this.origin = builder.origin;
    }

    @Override
    public Builder<?, E> toBuilder() {
        Builder<?, E> builder = new Builder<>(type, null);
        builder.supplier = supplier;
        builder.patterns.addAll(patterns);
        builder.priority = priority;
        return builder;
    }

    @Override
    public org.skriptlang.skript.docs.Origin origin() {
        return origin;
    }

    @Override
    public Class<E> type() {
        return type;
    }

    /** A new element, as each place a script uses the syntax gets. */
    @Override
    public E instance() {
        if (supplier != null) {
            return supplier.get();
        }
        try {
            java.lang.reflect.Constructor<E> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Can't make a " + type.getName() + ": " + e, e);
        }
    }

    @Override
    public SequencedCollection<String> patterns() {
        return patterns;
    }

    @Override
    public Priority priority() {
        return priority;
    }

    /** Builds any syntax info. */
    @SuppressWarnings("unchecked")
    public static class Builder<B extends Builder<B, E>, E extends SyntaxElement> implements SyntaxInfo.Builder<B, E> {

        final Class<E> type;
        Supplier<E> supplier;
        final List<String> patterns = new ArrayList<>();
        Priority priority = SyntaxInfo.SIMPLE;
        org.skriptlang.skript.docs.Origin origin;

        public Builder(Class<E> type, Supplier<E> supplier) {
            this.type = type;
            this.supplier = supplier;
        }

        @Override
        public B origin(org.skriptlang.skript.docs.Origin origin) {
            this.origin = origin;
            return (B) this;
        }

        @Override
        public B supplier(Supplier<E> supplier) {
            this.supplier = supplier;
            return (B) this;
        }

        @Override
        public B addPattern(String pattern) {
            patterns.add(pattern);
            return (B) this;
        }

        @Override
        public B addPatterns(String... patterns) {
            this.patterns.addAll(List.of(patterns));
            return (B) this;
        }

        @Override
        public B addPatterns(Collection<String> patterns) {
            this.patterns.addAll(patterns);
            return (B) this;
        }

        @Override
        public B clearPatterns() {
            patterns.clear();
            return (B) this;
        }

        @Override
        public B priority(Priority priority) {
            this.priority = priority == null ? SyntaxInfo.SIMPLE : priority;
            return (B) this;
        }

        @Override
        public SyntaxInfo<E> build() {
            return new CompatSyntaxInfo<>(this);
        }

        @Override
        public void applyTo(SyntaxInfo.Builder<?, ?> builder) {
            ((SyntaxInfo.Builder<?, E>) builder).addPatterns(patterns);
            if (supplier != null) {
                ((SyntaxInfo.Builder<?, E>) builder).supplier(supplier);
            }
            builder.priority(priority);
        }
    }

    /** An expression's info. */
    public static final class ExpressionInfo<E extends ch.njol.skript.lang.Expression<R>, R> extends CompatSyntaxInfo<E>
            implements DefaultSyntaxInfos.Expression<E, R> {

        final Class<R> returnType;

        ExpressionInfo(ExpressionBuilder<?, E, R> builder) {
            super(builder);
            this.returnType = builder.returnType;
        }

        @Override
        public ExpressionBuilder<?, E, R> toBuilder() {
            ExpressionBuilder<?, E, R> builder = new ExpressionBuilder<>(type, returnType);
            builder.supplier = supplier;
            builder.patterns.addAll(patterns);
            builder.priority = priority;
            return builder;
        }

        @Override
        public Class<R> returnType() {
            return returnType;
        }
    }

    /** Builds an expression's info. */
    public static final class ExpressionBuilder<B extends ExpressionBuilder<B, E, R>, E extends ch.njol.skript.lang.Expression<R>, R>
            extends Builder<B, E> implements DefaultSyntaxInfos.Expression.Builder<B, E, R> {

        final Class<R> returnType;

        public ExpressionBuilder(Class<E> type, Class<R> returnType) {
            super(type, null);
            this.returnType = returnType;
        }

        @Override
        public DefaultSyntaxInfos.Expression<E, R> build() {
            return new ExpressionInfo<>(this);
        }
    }

    /** A structure's info. */
    public static final class StructureInfo<E extends org.skriptlang.skript.lang.structure.Structure> extends CompatSyntaxInfo<E>
            implements DefaultSyntaxInfos.Structure<E> {

        final EntryValidator entryValidator;
        final NodeType nodeType;

        StructureInfo(StructureBuilder<?, E> builder) {
            super(builder);
            this.entryValidator = builder.entryValidator;
            this.nodeType = builder.nodeType;
        }

        @Override
        public StructureBuilder<?, E> toBuilder() {
            StructureBuilder<?, E> builder = new StructureBuilder<>(type);
            builder.supplier = supplier;
            builder.patterns.addAll(patterns);
            builder.entryValidator = entryValidator;
            builder.nodeType = nodeType;
            return builder;
        }

        @Override
        public EntryValidator entryValidator() {
            return entryValidator;
        }

        @Override
        public NodeType nodeType() {
            return nodeType;
        }
    }

    /** Builds a structure's info. */
    @SuppressWarnings("unchecked")
    public static final class StructureBuilder<B extends StructureBuilder<B, E>, E extends org.skriptlang.skript.lang.structure.Structure>
            extends Builder<B, E> implements DefaultSyntaxInfos.Structure.Builder<B, E> {

        EntryValidator entryValidator;
        DefaultSyntaxInfos.Structure.NodeType nodeType = DefaultSyntaxInfos.Structure.NodeType.SECTION;

        public StructureBuilder(Class<E> type) {
            super(type, null);
        }

        @Override
        public B entryValidator(EntryValidator entryValidator) {
            this.entryValidator = entryValidator;
            return (B) this;
        }

        @Override
        public B nodeType(DefaultSyntaxInfos.Structure.NodeType type) {
            this.nodeType = type;
            return (B) this;
        }

        @Override
        public DefaultSyntaxInfos.Structure<E> build() {
            return new StructureInfo<>(this);
        }
    }

    /** An event's info. */
    public static final class EventInfo<E extends SkriptEvent> extends CompatSyntaxInfo<E>
            implements BukkitSyntaxInfos.Event<E> {

        final String name;
        final List<Class<? extends org.bukkit.event.Event>> events;

        EventInfo(EventBuilder<?, E> builder) {
            super(builder);
            this.name = builder.name;
            this.events = List.copyOf(builder.events);
        }

        @Override
        public EventBuilder<?, E> toBuilder() {
            EventBuilder<?, E> builder = new EventBuilder<>(type, name);
            builder.supplier = supplier;
            builder.patterns.addAll(patterns);
            builder.events.addAll(events);
            return builder;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Collection<Class<? extends org.bukkit.event.Event>> events() {
            return events;
        }
    }

    /** Builds an event's info. */
    @SuppressWarnings("unchecked")
    public static final class EventBuilder<B extends EventBuilder<B, E>, E extends SkriptEvent> extends Builder<B, E>
            implements BukkitSyntaxInfos.Event.Builder<B, E> {

        final String name;
        final List<Class<? extends org.bukkit.event.Event>> events = new ArrayList<>();

        public EventBuilder(Class<E> type, String name) {
            super(type, null);
            this.name = name;
        }

        @Override
        public B addEvent(Class<? extends org.bukkit.event.Event> event) {
            events.add(event);
            return (B) this;
        }

        @Override
        public B addEvents(Collection<Class<? extends org.bukkit.event.Event>> events) {
            this.events.addAll(events);
            return (B) this;
        }

        @Override
        public BukkitSyntaxInfos.Event<E> build() {
            return new EventInfo<>(this);
        }
    }
}
