package co.xenastudios.neoskript.core.runtime;

/**
 * Thrown by the {@code exit loop} effect to break out of the innermost loop. A lightweight
 * control-flow signal (no stack trace).
 */
public final class BreakSignal extends RuntimeException {

    public static final BreakSignal INSTANCE = new BreakSignal();

    private BreakSignal() {
        super(null, null, false, false);
    }
}
