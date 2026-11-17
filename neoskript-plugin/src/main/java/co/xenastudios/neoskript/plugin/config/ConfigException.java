/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

/** The config file couldn't be read at all, such as a YAML syntax error. */
public final class ConfigException extends Exception {

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
