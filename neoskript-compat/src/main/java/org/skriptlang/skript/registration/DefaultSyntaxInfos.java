/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.registration;

import java.util.function.Supplier;

/** Skript's own kinds of syntax info: expressions (with a return type) and structures. */
public sealed interface DefaultSyntaxInfos permits SyntaxInfo {

    /** An expression's syntax info: also its return type. */
    interface Expression<E extends ch.njol.skript.lang.Expression<R>, R> extends SyntaxInfo<E> {

        static <E extends ch.njol.skript.lang.Expression<R>, R> Expression<E, R> simple(Class<E> expressionClass,
                Class<R> returnType, Supplier<E> supplier, String... patterns) {
            return builder(expressionClass, returnType).supplier(supplier).addPatterns(patterns).build();
        }

        static <E extends ch.njol.skript.lang.Expression<R>, R> Builder<? extends Builder<?, E, R>, E, R> builder(
                Class<E> expressionClass, Class<R> returnType) {
            return new co.xenastudios.neoskript.compat.CompatSyntaxInfo.ExpressionBuilder<>(expressionClass, returnType);
        }

        @Override
        Builder<? extends Builder<?, E, R>, E, R> toBuilder();

        Class<R> returnType();

        /** Builds an expression's syntax info. */
        interface Builder<B extends Builder<B, E, R>, E extends ch.njol.skript.lang.Expression<R>, R>
                extends SyntaxInfo.Builder<B, E> {

            @Override
            Expression<E, R> build();
        }
    }

    /** A structure's syntax info: also its entries. */
    interface Structure<E extends org.skriptlang.skript.lang.structure.Structure> extends SyntaxInfo<E> {

        /** Whether the structure is a line, a section, or either. */
        enum NodeType {
            SIMPLE, SECTION, BOTH;

            public boolean canBeSimple() {
                return this != SECTION;
            }

            public boolean canBeSection() {
                return this != SIMPLE;
            }
        }

        static <E extends org.skriptlang.skript.lang.structure.Structure> Structure<E> simple(Class<E> structureClass,
                Supplier<E> supplier, org.skriptlang.skript.lang.entry.EntryValidator entryValidator, String... patterns) {
            return builder(structureClass).supplier(supplier).entryValidator(entryValidator).addPatterns(patterns).build();
        }

        static <E extends org.skriptlang.skript.lang.structure.Structure> Builder<? extends Builder<?, E>, E> builder(
                Class<E> structureClass) {
            return new co.xenastudios.neoskript.compat.CompatSyntaxInfo.StructureBuilder<>(structureClass);
        }

        @Override
        Builder<? extends Builder<?, E>, E> toBuilder();

        org.skriptlang.skript.lang.entry.EntryValidator entryValidator();

        NodeType nodeType();

        /** Builds a structure's syntax info. */
        interface Builder<B extends Builder<B, E>, E extends org.skriptlang.skript.lang.structure.Structure>
                extends SyntaxInfo.Builder<B, E> {

            B entryValidator(org.skriptlang.skript.lang.entry.EntryValidator entryValidator);

            B nodeType(NodeType type);

            @Override
            Structure<E> build();
        }
    }
}
