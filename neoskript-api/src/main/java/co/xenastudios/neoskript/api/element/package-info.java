/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
/**
 * Syntax elements: effects, conditions, expressions, sections and functions, as the compiler calls them.
 *
 * <p>The compiler evaluates an element's placeholders and calls it with their values. Elements don't
 * evaluate their own sub-expressions, so the compiler controls evaluation order and can generate
 * bytecode for the common cases directly. An addon's elements are called through, and the JIT inlines them.
 */
package co.xenastudios.neoskript.api.element;
