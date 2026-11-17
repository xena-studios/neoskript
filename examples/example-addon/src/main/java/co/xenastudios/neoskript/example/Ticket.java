/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.example;

/**
 * A lottery ticket: the example addon's type, {@code ticket} in scripts.
 *
 * @param owner whose ticket it is
 * @param number its number, counting from 1
 */
public record Ticket(String owner, long number) {

    @Override
    public String toString() {
        return "ticket #" + number + " of " + owner;
    }
}
