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
 * <p>For now it's {@link SkriptDocsRegistry}: Skript 2.17.0's docs export, built into a registry the way
 * the frontend's corpus tests build theirs. It will be NeoSkript's own registry export, taken on a real
 * server, once that exists.
 */
public interface RegistrySource {

    /** The source the browser build uses. */
    RegistrySource CURRENT = new SkriptDocsRegistry();

    /** What the page says it parses against, e.g. {@code Skript 2.17.0 syntax}. */
    String describe(String data);

    SyntaxRegistry load(String data);
}
