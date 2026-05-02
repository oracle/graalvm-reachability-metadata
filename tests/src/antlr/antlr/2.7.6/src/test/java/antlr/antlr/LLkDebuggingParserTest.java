/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.Token;
import antlr.TokenBuffer;
import antlr.TokenStream;
import antlr.TokenStreamException;
import antlr.debug.LLkDebuggingParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LLkDebuggingParserTest {
    private static final String PARSE_VIEW_CLASS_NAME = "antlr.parseview.ParseView";
    private static final String SWING_BUTTON_CLASS_NAME = "javax.swing.JButton";

    @Test
    void setupDebuggingInstantiatesParseViewFromContextClassLoader() {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        LLkDebuggingParser parser = new LLkDebuggingParser(1);
        TokenStream tokenStream = new EndOfFileTokenStream();
        TokenBuffer tokenBuffer = new TokenBuffer(tokenStream);
        ParseViewReplacement.reset();

        try {
            parser.setDebugMode(false);
            Thread.currentThread().setContextClassLoader(new ParseViewClassLoader(previousContextClassLoader));

            parser.setupDebugging(tokenBuffer);

            assertThat(parser.isDebugMode()).isTrue();
            assertThat(ParseViewReplacement.instanceCount).isEqualTo(1);
            assertThat(ParseViewReplacement.lastParser).isSameAs(parser);
            assertThat(ParseViewReplacement.lastLexer).isNull();
            assertThat(ParseViewReplacement.lastTokenBuffer).isSameAs(tokenBuffer);
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            ParseViewReplacement.reset();
        }
    }

    public static final class ParseViewReplacement {
        private static int instanceCount;
        private static LLkDebuggingParser lastParser;
        private static TokenStream lastLexer;
        private static TokenBuffer lastTokenBuffer;

        public ParseViewReplacement(LLkDebuggingParser parser, TokenStream lexer, TokenBuffer tokenBuffer) {
            instanceCount++;
            lastParser = parser;
            lastLexer = lexer;
            lastTokenBuffer = tokenBuffer;
        }

        private static void reset() {
            instanceCount = 0;
            lastParser = null;
            lastLexer = null;
            lastTokenBuffer = null;
        }
    }

    private static final class ParseViewClassLoader extends ClassLoader {
        private ParseViewClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (PARSE_VIEW_CLASS_NAME.equals(name)) {
                return ParseViewReplacement.class;
            }
            if (SWING_BUTTON_CLASS_NAME.equals(name)) {
                return Object.class;
            }
            return super.loadClass(name);
        }
    }

    private static final class EndOfFileTokenStream implements TokenStream {
        @Override
        public Token nextToken() throws TokenStreamException {
            return new Token(Token.EOF_TYPE, "<EOF>");
        }
    }
}
