/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.web;

import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;

/**
 * Where the browser parser's syntax comes from: the text of a data file the page fetches, turned into a
 * registry. This is the one place to change when the data changes.
 *
 * <p>It's {@link NeoSkriptRegistry}: the syntax export NeoSkript wrote on a real server, so the page checks what
 * NeoSkript registers.
 */
public interface RegistrySource {

    /** The source the browser build uses. */
    RegistrySource CURRENT = new NeoSkriptRegistry();

    /** What the page says it parses against, e.g. {@code NeoSkript 0.1.0 on Paper 26.3}. */
    String describe(String data);

    SyntaxRegistry load(String data);
}
