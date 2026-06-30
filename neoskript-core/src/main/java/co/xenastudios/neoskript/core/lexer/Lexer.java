package co.xenastudios.neoskript.core.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns script source text into a stream of {@link Token}s.
 *
 * <p>This is the first stage of the parse pipeline. Phase 0 implements single-line tokenization
 * (words, numbers, double-quoted strings, and punctuation); indentation handling, multi-line
 * constructs, and escape sequences arrive in Phase 1 alongside the parser.
 */
public final class Lexer {

    /**
     * Tokenizes a single line of source.
     *
     * @param line       the line text (without trailing newline)
     * @param lineNumber the 1-based line number, used for token positions
     * @return the tokens on the line, terminated by an {@link TokenType#EOF} token
     */
    public List<Token> tokenizeLine(String line, int lineNumber) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = line.length();

        while (i < n) {
            char c = line.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            int column = i + 1;

            if (c == '"') {
                int start = ++i;
                while (i < n && line.charAt(i) != '"') {
                    i++;
                }
                tokens.add(new Token(TokenType.STRING, line.substring(start, i), lineNumber, column));
                if (i < n) {
                    i++; // consume the closing quote
                }
            } else if (Character.isDigit(c)) {
                int start = i;
                while (i < n && (Character.isDigit(line.charAt(i)) || line.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, line.substring(start, i), lineNumber, column));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) {
                    i++;
                }
                tokens.add(new Token(TokenType.WORD, line.substring(start, i), lineNumber, column));
            } else {
                tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c), lineNumber, column));
                i++;
            }
        }

        tokens.add(new Token(TokenType.EOF, "", lineNumber, n + 1));
        return tokens;
    }
}
