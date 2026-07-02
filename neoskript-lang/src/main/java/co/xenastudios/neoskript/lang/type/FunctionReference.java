package co.xenastudios.neoskript.lang.type;

/**
 * A reference to a (user-defined) function by name — the value produced by {@code function named
 * "foo"} and consumed by {@code run %executable%} / {@code result of %executable%}. Resolved against
 * the active function registry when run.
 */
public final class FunctionReference {

    private final String name;

    public FunctionReference(String name) {
        this.name = name;
    }

    /** @return the referenced function's name */
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name + "()";
    }
}
