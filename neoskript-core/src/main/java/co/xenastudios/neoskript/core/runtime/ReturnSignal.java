package co.xenastudios.neoskript.core.runtime;

/**
 * Thrown by the {@code return} effect to exit a function with a value. Caught by the function-call
 * machinery. A lightweight control-flow signal (no stack trace) rather than an error.
 */
public final class ReturnSignal extends RuntimeException {

    private final transient Object value;

    public ReturnSignal(Object value) {
        super(null, null, false, false);
        this.value = value;
    }

    /** @return the returned value, possibly {@code null} */
    public Object value() {
        return value;
    }
}
