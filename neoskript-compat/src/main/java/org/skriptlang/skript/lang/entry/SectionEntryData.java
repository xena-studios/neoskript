/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.lang.entry;

import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;

/** An entry that's a section of its own. NeoSkriptCompat doesn't read sections' entries yet. */
public class SectionEntryData extends EntryData<SectionNode> {

    public SectionEntryData(String key, SectionNode defaultValue, boolean optional) {
        super(key, defaultValue, optional);
    }

    public SectionEntryData(String key, SectionNode defaultValue, boolean optional, boolean multiple) {
        super(key, defaultValue, optional, multiple);
    }

    @Override
    public SectionNode getValue(Node node) {
        throw new UnsupportedOperationException("NeoSkriptCompat doesn't read sections' entries yet");
    }

    @Override
    public boolean canCreateWith(Node node) {
        return false;
    }
}
