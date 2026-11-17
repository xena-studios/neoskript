/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang;

/** A line that does something or checks something: an effect, a condition or a section. */
public abstract class Statement extends TriggerItem implements SyntaxElement {

    protected Statement() {}
}
