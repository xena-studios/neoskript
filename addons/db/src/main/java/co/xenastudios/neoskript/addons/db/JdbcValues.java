/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Values between scripts and JDBC, as skript-db 2.0 converts them. From a database: numbers as whole numbers or
 * decimals, dates and times as dates ({@link Instant}, which NeoSkript makes a date), times of day, UUIDs and
 * bytes as text, arrays as lists. To a database: texts, numbers, booleans and bytes as they are, dates as
 * timestamps, anything else as its text.
 */
final class JdbcValues {

    private JdbcValues() {}

    /**
     * A value read from a result, as scripts have it.
     *
     * @param value what the driver gave
     * @return a text, a {@code Long}, a {@code Double}, a {@code Boolean}, an {@link Instant}, a list of those, or
     *     {@code null}
     * @throws SQLException if a large value can't be read
     */
    static Object fromJdbc(Object value) throws SQLException {
        return switch (value) {
            case null -> null;
            case String text -> text;
            case Boolean bool -> bool;
            case Long number -> number;
            case Integer number -> number.longValue();
            case Short number -> number.longValue();
            case Byte number -> number.longValue();
            case Double number -> number;
            // As the float reads, not its binary value: 1.1, not 1.100000023841858.
            case Float number -> Double.parseDouble(number.toString());
            case BigDecimal decimal -> fromDecimal(decimal);
            case BigInteger integer -> integer.bitLength() < 64 ? (Object) integer.longValue() : integer.doubleValue();
            case Timestamp timestamp -> timestamp.toInstant();
            case java.sql.Date date -> date.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
            case java.sql.Time time -> time.toLocalTime().toString();
            case java.util.Date date -> date.toInstant();
            case LocalDateTime dateTime -> dateTime.atZone(ZoneId.systemDefault()).toInstant();
            case LocalDate date -> date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            case OffsetDateTime dateTime -> dateTime.toInstant();
            case ZonedDateTime dateTime -> dateTime.toInstant();
            case Instant instant -> instant;
            case LocalTime time -> time.toString();
            case UUID uuid -> uuid.toString();
            case Character character -> character.toString();
            case byte[] bytes -> new String(bytes, StandardCharsets.UTF_8);
            case Blob blob -> fromBlob(blob);
            case Clob clob -> fromClob(clob);
            case Array array -> fromArray(array);
            default -> value.toString();
        };
    }

    private static Object fromDecimal(BigDecimal decimal) {
        BigDecimal plain = decimal.stripTrailingZeros();
        if (plain.scale() <= 0 && plain.precision() - plain.scale() <= 18) {
            return plain.longValueExact();
        }
        return decimal.doubleValue();
    }

    private static String fromBlob(Blob blob) throws SQLException {
        try {
            long length = blob.length();
            return length == 0 ? "" : new String(blob.getBytes(1, (int) Math.min(length, Integer.MAX_VALUE)),
                    StandardCharsets.UTF_8);
        } finally {
            try {
                blob.free();
            } catch (SQLException | UnsupportedOperationException ignored) {
                // Freed with the result anyway.
            }
        }
    }

    private static String fromClob(Clob clob) throws SQLException {
        try {
            long length = clob.length();
            return length == 0 ? "" : clob.getSubString(1, (int) Math.min(length, Integer.MAX_VALUE));
        } finally {
            try {
                clob.free();
            } catch (SQLException | UnsupportedOperationException ignored) {
                // Freed with the result anyway.
            }
        }
    }

    private static List<Object> fromArray(Array array) throws SQLException {
        try {
            Object raw = array.getArray();
            if (!(raw instanceof Object[] elements)) {
                return Collections.singletonList(raw == null ? null : raw.toString());
            }
            List<Object> converted = new ArrayList<>(elements.length);
            for (Object element : elements) {
                converted.add(fromJdbc(element));
            }
            return converted;
        } finally {
            try {
                array.free();
            } catch (SQLException | UnsupportedOperationException ignored) {
                // Freed with the result anyway.
            }
        }
    }

    /**
     * A script's value as a statement's parameter.
     *
     * @param value the value
     * @param text how the value reads as text, for anything JDBC doesn't take as it is
     * @return what to bind
     */
    static Object toJdbc(Object value, String text) {
        return switch (value) {
            case null -> null;
            case String string -> string;
            case Number number -> number;
            case Boolean bool -> bool;
            case byte[] bytes -> bytes;
            default -> {
                long millis = ScriptValues.dateMillis(value);
                yield millis != Long.MIN_VALUE ? new Timestamp(millis) : text;
            }
        };
    }
}
