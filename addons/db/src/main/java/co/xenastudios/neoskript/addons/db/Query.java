/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.db;

import java.util.List;

/**
 * SQL with its parameters.
 *
 * @param sql the SQL, with a {@code ?} for each parameter
 * @param parameters what to bind, in order
 */
record Query(String sql, List<Object> parameters) {

    Query {
        parameters = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parameters));
    }

    static Query of(String sql) {
        return new Query(sql, List.of());
    }
}
