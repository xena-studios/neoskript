/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.text.SimpleDateFormat;

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

    /** As Skript writes dates by default. */
    @Override
    public String toString() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new java.util.Date(millis));
    }
}
