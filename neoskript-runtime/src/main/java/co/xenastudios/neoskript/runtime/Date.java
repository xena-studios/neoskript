/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;


/** A point in time, Skript's {@code date}: milliseconds since the Unix epoch. */
public record Date(long millis) implements Comparable<Date> {

    public static Date now() {
        return new Date(System.currentTimeMillis());
    }

    /** How long from this date to {@code other}, either way round. */
    public Timespan difference(Date other) {
        return new Timespan(Math.abs(other.millis - millis));
    }

    @Override
    public int compareTo(Date other) {
        return Long.compare(millis, other.millis);
    }

    /** As Skript writes dates: in config.yml's {@code text.date-format}, the locale's short date and time by default. */
    @Override
    public String toString() {
        return Settings.formatDate(millis);
    }
}
