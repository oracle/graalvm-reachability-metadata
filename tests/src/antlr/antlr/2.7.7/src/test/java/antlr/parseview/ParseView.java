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

public final class ParseView {
    public static int invocationCount;
    public static LLkDebuggingParser lastParser;
    public static TokenStream lastTokenStream;
    public static TokenBuffer lastTokenBuffer;

    public ParseView(LLkDebuggingParser parser, TokenStream tokenStream, TokenBuffer tokenBuffer) {
        invocationCount++;
        lastParser = parser;
        lastTokenStream = tokenStream;
        lastTokenBuffer = tokenBuffer;
    }

    public static void reset() {
        invocationCount = 0;
        lastParser = null;
        lastTokenStream = null;
        lastTokenBuffer = null;
    }
}
