/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.TriggerContext;

/**
 * What calls to a script function go through. Each caller holds the handle, not the function, so a
 * reload can swap in a new version of the function without recompiling every script that calls it.
 */
public final class FunctionHandle {

    private static final SimpleTriggerContext CONTEXT = new SimpleTriggerContext(null);

    private final String name;
    private volatile CompiledFunction target;
    private volatile String script;

    public FunctionHandle(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** The script that defines it, or {@code null} if it hasn't been compiled yet. */
    public String script() {
        return script;
    }

    public void script(String definedIn) {
        this.script = definedIn;
    }

    /** Whether it's loaded, so it can be called. */
    public boolean loaded() {
        return target != null;
    }

    public void target(CompiledFunction target) {
        this.target = target;
    }

    public Object call(Object[] arguments) {
        return call(CONTEXT, arguments);
    }

    /**
     * Calls it from code running in {@code caller}: if it waits, it carries on where the caller runs (its
     * region, on Folia).
     */
    public Object call(TriggerContext caller, Object[] arguments) {
        return KeyedValues.plain(callKeyed(caller, arguments));
    }

    /** What the function returns, with the indices of a list it returns ({@link KeyedValues}), as {@code keyed f()} wants. */
    public Object callKeyed(TriggerContext caller, Object[] arguments) {
        CompiledFunction function = target;
        if (function == null) {
            throw new IllegalStateException("The function " + name + " isn't loaded");
        }
        Object owner = caller instanceof SimpleTriggerContext simple ? simple.owner() : null;
        return function.call(owner == null ? CONTEXT : new SimpleTriggerContext(null, owner), arguments);
    }
}
