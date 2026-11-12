/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.web;

import org.teavm.jso.JSExport;

/**
 * The browser build's entry points, exported from the ES module TeaVM writes. The page's Web Worker calls
 * {@link #init(String)} once with the syntax data, then {@link #parse(String)} for each edit.
 */
public final class Main {

    private static WebParser parser;

    private Main() {}

    public static void main(String[] args) {}

    /** Builds the registry from the syntax data; returns what it parses against, for the page to show. */
    @JSExport
    public static String init(String syntaxData) {
        parser = new WebParser(RegistrySource.CURRENT.load(syntaxData));
        return RegistrySource.CURRENT.describe(syntaxData);
    }

    /** Parses one script; see {@link WebParser} for the JSON it returns. */
    @JSExport
    public static String parse(String source) {
        if (parser == null) {
            throw new IllegalStateException("init first");
        }
        return parser.parse(source);
    }

    /**
     * Parses one script of several, for an editor: {@code context} is the declarations of the functions the other
     * scripts define (a script of their headers), so calls to them aren't unknown. Only {@code source} is reported.
     */
    @JSExport
    public static String parseWith(String source, String context) {
        if (parser == null) {
            throw new IllegalStateException("init first");
        }
        return parser.parse(source, context);
    }
}
