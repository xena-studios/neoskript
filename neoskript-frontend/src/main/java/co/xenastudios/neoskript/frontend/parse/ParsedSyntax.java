/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.pattern.MatchResult;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;

/**
 * A syntax element matched against some text. {@code match.values()} holds the {@link Expr} for each
 * placeholder, and {@code match.regexValues()} what each regex's text parsed to (an {@link Expr},
 * {@link ParsedSyntax} or {@link co.xenastudios.neoskript.frontend.registry.TypeRef}).
 *
 * @param pattern the index of the pattern that matched
 */
public record ParsedSyntax(SyntaxInfo syntax, int pattern, MatchResult match) {}
