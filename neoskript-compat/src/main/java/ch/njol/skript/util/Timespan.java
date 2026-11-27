/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util;

import ch.njol.skript.lang.ParseContext;
import ch.njol.yggdrasil.YggdrasilSerializable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.List;

/**
 * Skript's timespan ({@code 1 minute}), for addons: a length of time in milliseconds, as Skript's is. Scripts get
 * NeoSkript's timespan of the same length; text and parsing are NeoSkript's, which match Skript's.
 */
public class Timespan implements YggdrasilSerializable, Comparable<Timespan>, TemporalAmount {

    private final long millis;

    public Timespan() {
        this(0);
    }

    public Timespan(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("millis must be >= 0");
        }
        this.millis = millis;
    }

    public Timespan(TimePeriod timePeriod, long time) {
        if (time < 0) {
            throw new IllegalArgumentException("time must be >= 0");
        }
        this.millis = time * timePeriod.getTime();
    }

    /** NeoSkript's timespan as Skript's: not one of Skript's methods. */
    public static Timespan of(Object neoskript) {
        return new Timespan(((co.xenastudios.neoskript.runtime.Timespan) neoskript).millis());
    }

    /** This as NeoSkript's timespan: not one of Skript's methods. */
    public Object neoskript() {
        return new co.xenastudios.neoskript.runtime.Timespan(millis);
    }

    public static Timespan parse(String value) {
        return parse(value, ParseContext.DEFAULT);
    }

    public static Timespan parse(String value, ParseContext context) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        co.xenastudios.neoskript.runtime.Timespan parsed = co.xenastudios.neoskript.runtime.Timespan.parse(value);
        return parsed == null || parsed.millis() < 0 ? null : new Timespan(parsed.millis());
    }

    public static Timespan fromDuration(Duration duration) {
        return new Timespan(duration.toMillis());
    }

    public static Timespan infinite() {
        return new Timespan(Long.MAX_VALUE);
    }

    public static String toString(long millis) {
        return new co.xenastudios.neoskript.runtime.Timespan(millis).toString();
    }

    public static String toString(long millis, int flags) {
        return toString(millis);
    }

    @Deprecated
    public static Timespan fromTicks(long ticks) {
        return new Timespan(ticks * 50L);
    }

    @Deprecated
    public static Timespan fromTicks_i(long ticks) {
        return new Timespan(ticks * 50L);
    }

    @Deprecated
    public long getMilliSeconds() {
        return millis;
    }

    @Deprecated
    public long getTicks() {
        return getAs(TimePeriod.TICK);
    }

    @Deprecated
    public long getTicks_i() {
        return getAs(TimePeriod.TICK);
    }

    public boolean isInfinite() {
        return millis == Long.MAX_VALUE;
    }

    public long getAs(TimePeriod timePeriod) {
        return millis / timePeriod.getTime();
    }

    public Duration getDuration() {
        return Duration.ofMillis(millis);
    }

    public Timespan add(Timespan timespan) {
        if (isInfinite() || timespan.isInfinite()) {
            return infinite();
        }
        long sum = millis + timespan.millis;
        return new Timespan(sum < 0 ? Long.MAX_VALUE : sum);
    }

    public Timespan subtract(Timespan timespan) {
        if (isInfinite() || timespan.isInfinite()) {
            return infinite();
        }
        return new Timespan(Math.max(0, millis - timespan.millis));
    }

    public Timespan multiply(double scalar) {
        if (scalar < 0) {
            throw new IllegalArgumentException();
        }
        if (Double.isInfinite(scalar)) {
            return infinite();
        }
        return new Timespan((long) Math.min(millis * scalar, Long.MAX_VALUE));
    }

    public Timespan divide(double scalar) {
        if (scalar < 0) {
            throw new IllegalArgumentException("Cannot divide a timespan by non-positive value");
        }
        if (isInfinite()) {
            return infinite();
        }
        double value = millis / scalar;
        if (Double.isNaN(value)) {
            return new Timespan(0);
        }
        if (Double.isInfinite(value)) {
            return infinite();
        }
        return new Timespan((long) Math.min(value, Long.MAX_VALUE));
    }

    public double divide(Timespan other) {
        if (isInfinite()) {
            return other.isInfinite() ? Double.NaN : Double.POSITIVE_INFINITY;
        } else if (other.isInfinite()) {
            return 0.0;
        }
        return millis / (double) other.millis;
    }

    public Timespan difference(Timespan timespan) {
        if (isInfinite() || timespan.isInfinite()) {
            return infinite();
        }
        return new Timespan(Math.abs(millis - timespan.millis));
    }

    @Override
    public long get(TemporalUnit unit) {
        if (unit instanceof TimePeriod period) {
            return component(period);
        }
        if (!(unit instanceof ChronoUnit chrono)) {
            throw new UnsupportedTemporalTypeException("Not a supported temporal unit: " + unit);
        }
        return switch (chrono) {
            case YEARS -> component(TimePeriod.YEAR);
            case MONTHS -> component(TimePeriod.MONTH);
            case WEEKS -> component(TimePeriod.WEEK);
            case DAYS -> component(TimePeriod.DAY);
            case HOURS -> component(TimePeriod.HOUR);
            case MINUTES -> component(TimePeriod.MINUTE);
            case SECONDS -> component(TimePeriod.SECOND);
            case MILLIS -> component(TimePeriod.MILLISECOND);
            default -> throw new UnsupportedTemporalTypeException("Not a supported time unit: " + chrono);
        };
    }

    /** The part of this in {@code period}, after the larger periods: Skript's. */
    private long component(TimePeriod period) {
        if (isInfinite()) {
            return Long.MAX_VALUE;
        }
        long remainder = millis;
        for (TimePeriod larger : TimePeriod.values()) {
            if (larger.ordinal() > period.ordinal()) {
                remainder %= larger.getTime();
            }
        }
        return remainder / period.getTime();
    }

    @Override
    public List<TemporalUnit> getUnits() {
        return List.<TemporalUnit>of(TimePeriod.values()).reversed();
    }

    @Override
    public Temporal addTo(Temporal temporal) {
        return temporal.plus(millis, ChronoUnit.MILLIS);
    }

    @Override
    public Temporal subtractFrom(Temporal temporal) {
        return temporal.minus(millis, ChronoUnit.MILLIS);
    }

    @Override
    public int compareTo(Timespan time) {
        return Long.compare(millis, time == null ? millis : time.millis);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(millis);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Timespan timespan && millis == timespan.millis;
    }

    @Override
    public String toString() {
        return toString(millis);
    }

    public String toString(int flags) {
        return toString(millis);
    }

    /** Skript's units of time, with their lengths. */
    public enum TimePeriod implements TemporalUnit {

        MILLISECOND(1L, "millisecond", "ms"),
        TICK(50L, "tick", "t"),
        SECOND(1000L, "second", "s"),
        MINUTE(60_000L, "minute", "m"),
        HOUR(3_600_000L, "hour", "h"),
        DAY(86_400_000L, "day", "d"),
        WEEK(7 * 86_400_000L, "week", "w"),
        MONTH(30 * 86_400_000L, "month", "mo"),
        YEAR(365 * 86_400_000L, "year", "y");

        private final long time;
        private final String fullForm;
        private final String shortForm;

        TimePeriod(long time, String fullForm, String shortForm) {
            this.time = time;
            this.fullForm = fullForm;
            this.shortForm = shortForm;
        }

        public long getTime() {
            return time;
        }

        public String getFullForm() {
            return fullForm;
        }

        public String getShortForm() {
            return shortForm;
        }

        @Override
        public Duration getDuration() {
            return Duration.ofMillis(time);
        }

        @Override
        public boolean isDurationEstimated() {
            return false;
        }

        @Override
        public boolean isDateBased() {
            return false;
        }

        @Override
        public boolean isTimeBased() {
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R extends Temporal> R addTo(R temporal, long amount) {
            return (R) temporal.plus(amount, this);
        }

        @Override
        public long between(Temporal temporal1Inclusive, Temporal temporal2Exclusive) {
            return temporal1Inclusive.until(temporal2Exclusive, this);
        }
    }
}
