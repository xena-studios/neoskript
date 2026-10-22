/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.source;

import java.util.ArrayList;
import java.util.List;

/** Collects the diagnostics for one script. */
public final class Diagnostics {

    private final SourceFile file;
    private final List<Diagnostic> all = new ArrayList<>();

    public Diagnostics(SourceFile file) {
        this.file = file;
    }

    public void error(int line, int column, int length, String message) {
        all.add(new Diagnostic(Severity.ERROR, file.name(), line, column, length, message));
    }

    public void warning(int line, int column, int length, String message) {
        all.add(new Diagnostic(Severity.WARNING, file.name(), line, column, length, message));
    }

    public List<Diagnostic> all() {
        return List.copyOf(all);
    }

    public boolean hasErrors() {
        return all.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
