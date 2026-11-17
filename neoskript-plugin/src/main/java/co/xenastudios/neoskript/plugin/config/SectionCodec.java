/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

import java.util.List;
import java.util.Map;

/**
 * A section whose keys are the owner's own, such as databases by name: read whole by this codec, and written back as
 * the owner wrote it, comments and all. The defaults are written as {@link #render} gives them.
 *
 * @param <T> what the section reads as
 */
public interface SectionCodec<T> {

    /**
     * Reads the section: its keys, in order, to plain values, lists and maps. What can't be used goes in
     * {@code warnings}, which start with the setting's path, and the rest is read as usual.
     *
     * @throws IllegalArgumentException if nothing in it can be used, so the default is used instead
     */
    T decode(Map<String, Object> section, String path, List<String> warnings);

    /** The section's contents as YAML lines, indented two spaces per level from none, with comments. */
    List<String> render(T value);

    /** The section's contents as plain values and maps, for adding it to a file that doesn't have it. */
    Map<String, Object> plain(T value);
}
