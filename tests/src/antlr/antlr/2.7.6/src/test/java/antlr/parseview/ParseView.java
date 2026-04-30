/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.parseview;

import antlr.TokenBuffer;
import antlr.TokenStream;
import antlr.debug.LLkDebuggingParser;

public class ParseView {
    private static int createdCount;
    private static LLkDebuggingParser parser;
    private static TokenStream lexer;
    private static TokenBuffer tokenBuffer;

    public ParseView(LLkDebuggingParser parser, TokenStream lexer, TokenBuffer tokenBuffer) {
        ParseView.createdCount++;
        ParseView.parser = parser;
        ParseView.lexer = lexer;
        ParseView.tokenBuffer = tokenBuffer;
    }

    public static void reset() {
        createdCount = 0;
        parser = null;
        lexer = null;
        tokenBuffer = null;
    }

    public static int createdCount() {
        return createdCount;
    }

    public static LLkDebuggingParser parser() {
        return parser;
    }

    public static TokenStream lexer() {
        return lexer;
    }

    public static TokenBuffer tokenBuffer() {
        return tokenBuffer;
    }
}
