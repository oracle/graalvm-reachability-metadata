/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.debug;

import antlr.Token;
import antlr.TokenBuffer;
import antlr.TokenStream;
import antlr.TokenStreamException;
import antlr.parseview.ParseView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LLkDebuggingParserTest {
    @AfterEach
    void resetParseView() {
        ParseView.reset();
    }

    @Test
    void setupDebuggingWithTokenStreamInstantiatesParseView() {
        RecordingTokenStream lexer = new RecordingTokenStream();
        LLkDebuggingParser parser = new LLkDebuggingParser(1);

        parser.setupDebugging(lexer);

        assertThat(parser.isDebugMode()).isTrue();
        assertThat(ParseView.invocationCount).isEqualTo(1);
        assertThat(ParseView.lastParser).isSameAs(parser);
        assertThat(ParseView.lastTokenStream).isSameAs(lexer);
        assertThat(ParseView.lastTokenBuffer).isNull();
    }

    @Test
    void setupDebuggingWithTokenBufferInstantiatesParseView() {
        RecordingTokenStream lexer = new RecordingTokenStream();
        TokenBuffer tokenBuffer = new TokenBuffer(lexer);
        LLkDebuggingParser parser = new LLkDebuggingParser(1);

        parser.setupDebugging(tokenBuffer);

        assertThat(parser.isDebugMode()).isTrue();
        assertThat(ParseView.invocationCount).isEqualTo(1);
        assertThat(ParseView.lastParser).isSameAs(parser);
        assertThat(ParseView.lastTokenStream).isNull();
        assertThat(ParseView.lastTokenBuffer).isSameAs(tokenBuffer);
    }

    static final class RecordingTokenStream implements TokenStream {
        @Override
        public Token nextToken() throws TokenStreamException {
            return new Token(Token.EOF_TYPE, "<EOF>");
        }
    }
}
