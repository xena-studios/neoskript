/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Syntax elements: effects, conditions and expressions, as the compiler calls them.
 *
 * <p>The compiler evaluates an element's placeholders and calls it with their values. Elements don't
 * evaluate their own sub-expressions, so the compiler controls evaluation order and can generate
 * bytecode for the common cases directly.
 */
package co.xenastudios.neoskript.api.element;
