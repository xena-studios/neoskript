/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.command;

/**
 * Implemented by the server commands scripts define, so syntax such as {@code "x" is a script command}
 * can tell them apart from other commands.
 */
public interface ScriptCommandInfo {

    /**
     * Why the last script command run on this thread didn't run, or {@code null} if it did: {@code execute
     * command} reports it as a runtime error.
     */
    ThreadLocal<String> FAILED = new ThreadLocal<>();

    /** What people without the command's permission are told, or {@code null} for the server's message. */
    String scriptPermissionMessage();
}
