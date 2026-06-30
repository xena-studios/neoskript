package co.xenastudios.neoskript.core.runtime;

/**
 * Thrown by the {@code stop} effect to abort the rest of the current trigger. Caught at the trigger
 * boundary. A lightweight control-flow signal (no stack trace) rather than an error.
 */
public final class StopSignal extends RuntimeException {

    public static final StopSignal INSTANCE = new StopSignal();

    private StopSignal() {
        super(null, null, false, false);
    }
}
