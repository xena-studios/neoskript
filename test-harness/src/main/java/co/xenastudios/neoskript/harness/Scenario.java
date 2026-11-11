/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness;

/** One thing the harness checks or measures on a server. */
public interface Scenario {

    /**
     * Quick checks run on every CI build, slower baseline runs (Skript's test suite, benchmarks), or tools run
     * on purpose by name, which no group selects (the syntax export).
     */
    enum Group {
        SMOKE,
        BASELINE,
        MANUAL
    }

    String name();

    Group group();

    boolean supports(Environment environment);

    /** Runs the scenario. Throws {@link HarnessFailure} (or any exception) when it fails. */
    void run(ScenarioContext context) throws Exception;
}
