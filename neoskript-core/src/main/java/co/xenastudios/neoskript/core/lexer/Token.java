package co.xenastudios.neoskript.core.lexer;

/**
 * A single lexical token with its source position, used by the parser for matching and for
 * span-accurate diagnostics.
 *
 * @param type   the token category
 * @param text   the token's source text (for {@link TokenType#STRING}, without the quotes)
 * @param line   the 1-based line number
 * @param column the 1-based column where the token starts
 */
public record Token(TokenType type, String text, int line, int column) {
}
