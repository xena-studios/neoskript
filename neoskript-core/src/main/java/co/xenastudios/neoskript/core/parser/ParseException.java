package co.xenastudios.neoskript.core.parser;

/**
 * Thrown when a script cannot be parsed. Carries the 1-based source line when known (0 if not), so
 * callers can produce span-accurate diagnostics.
 */
public final class ParseException extends RuntimeException {

    private final int line;

    public ParseException(String message) {
        this(message, 0);
    }

    public ParseException(String message, int line) {
        super(line > 0 ? "line " + line + ": " + message : message);
        this.line = line;
    }

    /** @return the 1-based source line, or {@code 0} if unknown */
    public int line() {
        return line;
    }
}
