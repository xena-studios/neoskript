/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.registry;

import co.xenastudios.neoskript.frontend.pattern.MatchResult;

/** A check a syntax element makes while it's parsed, given where it is in the trigger. */
@FunctionalInterface
public interface ParseCheck {

    /** An error that is the text written for an argument: this, then the argument's index. */
    String ARGUMENT_TEXT = "\u0000argument:";

    /** Returns an error to report, or {@code null} if the element is fine here. */
    String check(MatchResult match, DelayState delay);
}
