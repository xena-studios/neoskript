package co.xenastudios.neoskript.api.syntax;

/**
 * The ways a changeable {@link Expression} can be mutated by a {@code set}/{@code add}/{@code remove}/
 * {@code delete}/{@code reset} effect — NeoSkript's equivalent of Skript's {@code Changer.ChangeMode}.
 *
 * <p>An expression declares which modes it accepts via {@link Expression#acceptChange(ChangeMode)} and
 * performs the mutation in {@link Expression#change(co.xenastudios.neoskript.api.runtime.TriggerContext,
 * Object[], ChangeMode)}. A single generic effect dispatches to the expression, so a property only has
 * to implement its changer once to support every applicable mode.
 */
public enum ChangeMode {

    /** Replace the current value(s) with the supplied delta. */
    SET,

    /** Add the supplied delta to the current value(s). */
    ADD,

    /** Remove the supplied delta from the current value(s). */
    REMOVE,

    /** Remove all occurrences of the supplied delta. */
    REMOVE_ALL,

    /** Clear the value(s) entirely (no delta). */
    DELETE,

    /** Reset the value(s) to their default (no delta). */
    RESET
}
