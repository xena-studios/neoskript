/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util.slot;

/** A slot with an index: one of an inventory's. */
public abstract class SlotWithIndex extends Slot {

    public SlotWithIndex() {}

    public abstract int getIndex();

    public int getRawIndex() {
        return getIndex();
    }

    @Override
    public boolean isSameSlot(Slot other) {
        return other instanceof SlotWithIndex indexed && getIndex() == indexed.getIndex();
    }
}
