/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

/** Where storage that works in the background, such as an SQL database, tells the console what happened. */
public interface Console {

    void info(String message);

    void warn(String message);

    void error(String message);

    /** Prints nothing, for tests. */
    Console NONE = new Console() {
        @Override
        public void info(String message) {}

        @Override
        public void warn(String message) {}

        @Override
        public void error(String message) {}
    };
}
