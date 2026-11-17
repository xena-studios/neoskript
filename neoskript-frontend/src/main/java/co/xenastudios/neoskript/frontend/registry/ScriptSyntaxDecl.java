/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import co.xenastudios.neoskript.api.registration.ScriptSyntax;

/**
 * Syntax a script declared for one load, as {@link SyntaxInfo#script()} has it: what the addon read from the
 * structure, and the hidden function its code is compiled as.
 *
 * @param declared what the structure declares
 * @param body the function its body is compiled as; its uses call it
 * @param script the script that declares it
 * @param line the structure's line in that script
 */
public record ScriptSyntaxDecl(ScriptSyntax declared, FunctionInfo body, String script, int line) {

    /**
     * What every hidden function's name starts with: {@code custom syntax: <header> (<script>)}, the same from one
     * load to the next while the header is, so its handle is reused, and never a name a script can call (it has spaces).
     */
    public static final String FUNCTION_PREFIX = "custom syntax: ";
}
