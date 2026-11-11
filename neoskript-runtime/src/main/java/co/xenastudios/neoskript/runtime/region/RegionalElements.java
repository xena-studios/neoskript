/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.region;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.Section;
import co.xenastudios.neoskript.api.element.SettableCondition;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.runtime.errors.ErrorSites;

/**
 * Syntax elements as code compiled for Folia calls them: each checks region ownership around the element it
 * wraps, as {@link Regions} describes. The compiler wraps an element only where it needs to, so on Paper, and for
 * elements that touch no region, the element is called as it is.
 */
public final class RegionalElements {

    private RegionalElements() {}

    /**
     * {@code element}, called as {@code type}, wrapped to check region ownership.
     *
     * @param site where the element is called, for its errors
     * @param reads a bit for each argument whose placeholder takes entities or blocks, which it reads
     * @param subject the argument an effect or change acts on (the first that takes entities, else blocks or
     *     locations), or -1
     */
    public static Object wrap(Object element, Class<?> type, ErrorSites.Site site, long reads, int subject) {
        RegionAccess access = Regions.access();
        if (access == null) {
            return element;
        }
        if (type == Expression.class) {
            return new RegionalExpression((Expression) element, access, site, reads);
        }
        if (type == SettableCondition.class) {
            return new RegionalSettableCondition((SettableCondition) element, access, site, reads, subject);
        }
        if (type == Condition.class) {
            return new RegionalCondition((Condition) element, access, site, reads);
        }
        if (type == Section.class) {
            return new RegionalSection((Section) element, access, site, reads);
        }
        if (type == Effect.class) {
            return new RegionalEffect((Effect) element, access, site, subject);
        }
        if (type == Changeable.class) {
            return new RegionalChangeable((Changeable) element, access, site, subject);
        }
        return element;
    }

    /** Checks the entities and blocks an element reads; whether it may go on. */
    private static boolean readable(RegionAccess access, ErrorSites.Site site, Object[] arguments, long reads) {
        if (reads == 0) {
            return true;
        }
        Object foreign = Regions.foreign(access, arguments, reads);
        if (foreign == null) {
            return true;
        }
        Regions.readError(access, site, foreign);
        return false;
    }

    private record RegionalExpression(Expression element, RegionAccess access, ErrorSites.Site site, long reads)
            implements Expression, ErrorSites.Transparent {

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (!readable(access, site, arguments, reads)) {
                return null;
            }
            try {
                return element.get(context, arguments);
            } catch (RuntimeException e) {
                if (!Regions.violation(e)) {
                    throw e;
                }
                Regions.violationError(site, e);
                return null;
            }
        }
    }

    private record RegionalCondition(Condition element, RegionAccess access, ErrorSites.Site site, long reads)
            implements Condition, ErrorSites.Transparent {

        @Override
        public boolean check(TriggerContext context, Object[] arguments) {
            return RegionalElements.check(element, access, site, reads, context, arguments);
        }
    }

    private static boolean check(Condition element, RegionAccess access, ErrorSites.Site site, long reads,
            TriggerContext context, Object[] arguments) {
        if (!readable(access, site, arguments, reads)) {
            return false;
        }
        try {
            return element.check(context, arguments);
        } catch (RuntimeException e) {
            if (!Regions.violation(e)) {
                throw e;
            }
            Regions.violationError(site, e);
            return false;
        }
    }

    private record RegionalSettableCondition(SettableCondition element, RegionAccess access, ErrorSites.Site site,
            long reads, int subject) implements SettableCondition, ErrorSites.Transparent {

        @Override
        public boolean check(TriggerContext context, Object[] arguments) {
            return RegionalElements.check(element, access, site, reads, context, arguments);
        }

        @Override
        public void set(TriggerContext context, Object[] arguments, boolean value) {
            Regions.run(access, site, arguments, subject, args -> element.set(context, args, value));
        }
    }

    private record RegionalSection(Section element, RegionAccess access, ErrorSites.Site site, long reads)
            implements Section, ErrorSites.Transparent {

        @Override
        public Object run(TriggerContext context, Object[] arguments) {
            // Its code runs right after it, so it can't run elsewhere: it's read like an expression.
            if (!readable(access, site, arguments, reads)) {
                return null;
            }
            try {
                return element.run(context, arguments);
            } catch (RuntimeException e) {
                if (!Regions.violation(e)) {
                    throw e;
                }
                Regions.violationError(site, e);
                return null;
            }
        }
    }

    private record RegionalEffect(Effect element, RegionAccess access, ErrorSites.Site site, int subject)
            implements Effect, ErrorSites.Transparent {

        @Override
        public void execute(TriggerContext context, Object[] arguments) {
            Regions.run(access, site, arguments, subject, args -> element.execute(context, args));
        }
    }

    private record RegionalChangeable(Changeable element, RegionAccess access, ErrorSites.Site site, int subject)
            implements Changeable, ErrorSites.Transparent {

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return element.acceptChange(mode);
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Regions.run(access, site, arguments, subject, args -> element.change(context, args, delta, mode));
        }
    }
}
