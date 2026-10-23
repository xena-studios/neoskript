/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/**
 * A condition that can be made true or false, as {@code set whether {_pig} can breed to true} does. A
 * negated pattern ({@code can't breed}) is the element's to account for.
 */
public interface SettableCondition extends Condition {

    /** Makes the condition {@code value} for the values in {@code arguments}. */
    void set(TriggerContext context, Object[] arguments, boolean value);
}
