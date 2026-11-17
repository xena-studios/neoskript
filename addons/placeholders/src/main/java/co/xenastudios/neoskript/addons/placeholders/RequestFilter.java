/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.placeholders;

import co.xenastudios.neoskript.api.element.EventFilter;
import co.xenastudios.neoskript.api.element.TriggerContext;
import java.util.List;

/**
 * What a script's placeholder trigger is for: the prefixes in its line, as written, and whether it's relational. The
 * addon reads it to know which prefixes to register with PlaceholderAPI, and runs the trigger only for those, so the
 * check itself always passes.
 *
 * @param prefixes the prefixes, as written: {@code MyServer}
 * @param relational whether it answers relational placeholders ({@code %rel_myserver_x%})
 */
public record RequestFilter(List<String> prefixes, boolean relational) implements EventFilter {

    public RequestFilter {
        prefixes = List.copyOf(prefixes);
    }

    @Override
    public List<Class<?>> eventClasses() {
        return List.of(relational ? RelationalPlaceholderRequestEvent.class : PlaceholderRequestEvent.class);
    }

    @Override
    public boolean check(TriggerContext context, Object[] arguments) {
        return true;
    }
}
