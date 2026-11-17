/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.ScriptSyntaxCall;
import co.xenastudios.neoskript.api.element.TriggerContext;
import java.util.Set;

/**
 * One use of syntax a script declared, as its code runs: the context it's called with, which the code's own context
 * ({@link SimpleTriggerContext#call}) links to. It has the event, and the run, of where the syntax was used, so the
 * code runs as part of that run (its region on Folia, a transaction a run opened), and the use's arguments, pattern
 * and mark for skript-reflect's {@code expression-N}, {@code matched pattern} and {@code parser mark}.
 */
public final class ScriptSyntaxContext implements TriggerContext, ScriptSyntaxCall {

    private static final Object[] NONE = new Object[0];

    private final TriggerContext caller;
    private final Object[] arguments;
    private final int pattern;
    private final int mark;
    private final Set<String> tags;
    private volatile boolean proceeded;

    private ScriptSyntaxContext(TriggerContext caller, Object[] arguments, int pattern, int mark, Set<String> tags) {
        this.caller = caller;
        this.arguments = arguments == null ? NONE : arguments;
        this.pattern = pattern;
        this.mark = mark;
        this.tags = tags == null ? Set.of() : tags;
    }

    /** Runs a declared effect's code for one use. */
    public static void run(FunctionHandle code, TriggerContext caller, Object[] arguments, int pattern, int mark,
            Set<String> tags) {
        code.call(new ScriptSyntaxContext(caller, arguments, pattern, mark, tags), NONE);
    }

    /** Runs a declared condition's code for one use: it passes if the code proceeded. */
    public static boolean check(FunctionHandle code, TriggerContext caller, Object[] arguments, int pattern, int mark,
            Set<String> tags) {
        ScriptSyntaxContext context = new ScriptSyntaxContext(caller, arguments, pattern, mark, tags);
        code.call(context, NONE);
        return context.proceeded;
    }

    /** Runs a declared expression's code for one use: what it returned. */
    public static Object get(FunctionHandle code, TriggerContext caller, Object[] arguments, int pattern, int mark,
            Set<String> tags) {
        return code.call(new ScriptSyntaxContext(caller, arguments, pattern, mark, tags), NONE);
    }

    /** skript-reflect's {@code continue} in declared syntax's code: the use proceeds. */
    public static void proceed(TriggerContext code) {
        ScriptSyntaxCall use = code == null ? null : code.scriptSyntaxCall();
        if (use != null) {
            use.proceed();
        }
    }

    /** Where the syntax was used: its owner, so a wait in the code carries on on that thread on Folia. */
    Object owner() {
        return caller instanceof SimpleTriggerContext simple ? simple.owner()
                : caller instanceof ScriptSyntaxContext outer ? outer.owner() : null;
    }

    @Override
    public Object event() {
        return caller == null ? null : caller.event();
    }

    @Override
    public TriggerContext outermostRun() {
        return caller == null ? this : caller.outermostRun();
    }

    @Override
    public ScriptSyntaxCall scriptSyntaxCall() {
        return this;
    }

    @Override
    public Object[] arguments() {
        return arguments;
    }

    @Override
    public int pattern() {
        return pattern;
    }

    @Override
    public int mark() {
        return mark;
    }

    @Override
    public Set<String> tags() {
        return tags;
    }

    @Override
    public void proceed() {
        proceeded = true;
    }

    @Override
    public boolean proceeded() {
        return proceeded;
    }
}
