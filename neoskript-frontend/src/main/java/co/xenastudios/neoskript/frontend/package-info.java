/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * The frontend: lexer, structure parser, pattern compiler and matcher, type checker, AST, and
 * diagnostics.
 *
 * <p>Must not depend on Bukkit at runtime, so the CLI and language server can reuse it.
 */
package co.xenastudios.neoskript.frontend;
