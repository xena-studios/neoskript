/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

/**
 * The event a periodical trigger ({@code every 5 seconds}) runs in, as Skript's {@code ScheduledEvent}: its
 * {@code event-world} is the world it runs for ({@code every 5 seconds in "world"}), or {@code null}.
 *
 * @param world the world, or {@code null} for a periodical event without worlds
 */
public record PeriodicalEvent(Object world) {}
