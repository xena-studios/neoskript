/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness.scenario;

import co.xenastudios.neoskript.harness.Environment;
import co.xenastudios.neoskript.harness.HarnessFailure;
import co.xenastudios.neoskript.harness.Scenario;
import co.xenastudios.neoskript.harness.ScenarioContext;
import co.xenastudios.neoskript.harness.ServerProcess;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Items and inventories on a real server, whose registries item names and enchantments come from: item
 * literals with amounts, plurals and enchantments, names and lore, parsing items from text, and the
 * slots of a chest inventory.
 */
public final class NeoSkriptItemsScenario implements Scenario {

    private static final Duration WAIT = Duration.ofMinutes(2);

    @Override
    public String name() {
        return "neoskript-items";
    }

    @Override
    public Group group() {
        return Group.SMOKE;
    }

    @Override
    public boolean supports(Environment environment) {
        return true;
    }

    @Override
    public void run(ScenarioContext context) throws Exception {
        try (ServerProcess server = context.server()
                .plugin(context.neoskriptJar())
                .file("plugins/NeoSkript/scripts/items.sk", context.script("neoskript-items.sk"))
                .start()) {
            server.await(Pattern.compile("\\[harness] items done$"), WAIT);
            server.stop();
            List<String> problems = server.linesMatching(Pattern.compile("\\[harness] items FAIL|\\[NeoSkript] Line \\d+"));
            if (!problems.isEmpty()) {
                throw new HarnessFailure("Items didn't work: " + problems + ". See " + server.logFile());
            }
        }
    }
}
