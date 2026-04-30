/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.CommonToken;
import antlr.Token;
import antlr.TokenBuffer;
import antlr.TokenStream;
import antlr.TokenStreamException;
import antlr.debug.LLkDebuggingParser;
import antlr.parseview.ParseView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LLkDebuggingParserTest {
    @Test
    void setupDebuggingCreatesParseViewWithLexer() {
        ParseView.reset();
        TokenStream lexer = new EndOfInputTokenStream();
        LLkDebuggingParser parser = new LLkDebuggingParser(lexer, 1);
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(new ParseViewClassLoader(originalContextClassLoader));

            parser.setupDebugging(lexer);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }

        assertThat(parser.isDebugMode()).isTrue();
        assertThat(ParseView.createdCount()).isEqualTo(1);
        assertThat(ParseView.parser()).isSameAs(parser);
        assertThat(ParseView.lexer()).isSameAs(lexer);
        assertThat(ParseView.tokenBuffer()).isNull();
    }

    private static final class ParseViewClassLoader extends ClassLoader {
        private ParseViewClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if ("javax.swing.JButton".equals(name)) {
                return Object.class;
            }
            if ("antlr.parseview.ParseView".equals(name)) {
                return ParseView.class;
            }
            return super.loadClass(name);
        }
    }

    private static final class EndOfInputTokenStream implements TokenStream {
        @Override
        public Token nextToken() throws TokenStreamException {
            return new CommonToken(Token.EOF_TYPE, "<EOF>");
        }
    }
}
