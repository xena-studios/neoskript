/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

/** A scenario expectation that didn't hold. The message is shown in the summary. */
public final class HarnessFailure extends RuntimeException {

    public HarnessFailure(String message) {
        super(message);
    }
}
