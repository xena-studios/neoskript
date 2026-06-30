package co.xenastudios.neoskript.core.runtime;

/**
 * Thrown by the {@code continue} effect to skip to the next iteration of the innermost loop. A
 * lightweight control-flow signal (no stack trace).
 */
public final class ContinueSignal extends RuntimeException {

    public static final ContinueSignal INSTANCE = new ContinueSignal();

    private ContinueSignal() {
        super(null, null, false, false);
    }
}
