/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeInfo;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import java.util.List;
import java.util.Objects;

/**
 * What a placeholder accepts: its types (empty means anything), whether a list is accepted, whether only
 * literals are ({@code %*type%}, and everything in event headers), and whether the value has to be
 * something that can be changed ({@code %~type%}).
 *
 * <p>Used as a memo key for every sub-expression, so the hash is computed once.
 */
final class Expected {

    static final Expected OBJECTS = new Expected(List.of(), true, false, false);
    static final Expected OBJECT = new Expected(List.of(), false, false, false);

    private final List<TypeInfo> types;
    private final boolean plural;
    private final boolean literalOnly;
    private final boolean changeable;
    /** With some types single and others plural ({@code %number/booleans%}), the plural ones; else {@code null}. */
    private final List<TypeInfo> pluralTypes;
    private final int hash;

    Expected(List<TypeInfo> types, boolean plural, boolean literalOnly, boolean changeable) {
        this(types, plural, literalOnly, changeable, null);
    }

    private Expected(List<TypeInfo> types, boolean plural, boolean literalOnly, boolean changeable,
            List<TypeInfo> pluralTypes) {
        this.types = List.copyOf(types);
        this.plural = plural;
        this.literalOnly = literalOnly;
        this.changeable = changeable;
        this.pluralTypes = pluralTypes == null ? null : List.copyOf(pluralTypes);
        this.hash = Objects.hash(this.types, plural, literalOnly, changeable, this.pluralTypes);
    }

    static Expected of(List<TypeRef> refs, boolean literalOnly, boolean changeable) {
        boolean plural = false;
        boolean anything = refs.isEmpty();
        for (TypeRef ref : refs) {
            plural |= ref.plural();
            anything |= ref.type().id().equals(SyntaxRegistry.OBJECT);
        }
        List<TypeInfo> types = anything ? List.of() : refs.stream().map(TypeRef::type).toList();
        List<TypeInfo> pluralTypes = refs.stream().filter(TypeRef::plural).map(TypeRef::type).toList();
        boolean mixed = !anything && !pluralTypes.isEmpty() && pluralTypes.size() < refs.size();
        return new Expected(types, plural, literalOnly, changeable, mixed ? pluralTypes : null);
    }

    /**
     * Whether a list of several values of {@code type} fits: with {@code %number/booleans%}, only the plural
     * types take one, as in Skript.
     */
    boolean acceptsMany(String type, SyntaxRegistry registry) {
        if (pluralTypes == null || type == null || type.equals(SyntaxRegistry.OBJECT)) {
            return true;
        }
        for (TypeInfo expected : pluralTypes) {
            if (registry.isAssignable(type, expected.id())) {
                return true;
            }
        }
        return false;
    }

    List<TypeInfo> types() {
        return types;
    }

    boolean plural() {
        return plural;
    }

    boolean literalOnly() {
        return literalOnly;
    }

    boolean changeable() {
        return changeable;
    }

    boolean anything() {
        return types.isEmpty();
    }

    boolean accepts(String type, SyntaxRegistry registry) {
        if (anything() || type == null) {
            return true;
        }
        for (TypeInfo expected : types) {
            if (registry.isAssignable(type, expected.id())) {
                return true;
            }
        }
        return false;
    }

    boolean hasType(String id) {
        return types.stream().anyMatch(t -> t.id().equals(id));
    }

    Expected single() {
        return plural ? new Expected(types, false, literalOnly, changeable, null) : this;
    }

    Expected asLiteralOnly() {
        return literalOnly ? this : new Expected(types, plural, true, changeable, pluralTypes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Expected e && e.hash == hash && e.plural == plural && e.literalOnly == literalOnly
                && e.changeable == changeable && e.types.equals(types) && Objects.equals(e.pluralTypes, pluralTypes);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return (types.isEmpty() ? "object" : types.toString()) + (plural ? "s" : "") + (literalOnly ? " literal" : "")
                + (changeable ? " changeable" : "");
    }
}
