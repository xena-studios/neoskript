/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.registry;

/** A type as named somewhere, e.g. {@code players} is {@code player}, plural. */
public record TypeRef(TypeInfo type, boolean plural) {}
