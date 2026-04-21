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
    void setupDebuggingCreatesParseViewWithExpectedArguments() {
        ParseView.reset();
        TokenBuffer tokenBuffer = new TokenBuffer(new FixedTokenStream());
        LLkDebuggingParser parser = new LLkDebuggingParser(tokenBuffer, 1);

        parser.setupDebugging(tokenBuffer);

        assertThat(parser.isDebugMode()).isTrue();
        assertThat(ParseView.lastParser).isSameAs(parser);
        assertThat(ParseView.lastTokenStream).isNull();
        assertThat(ParseView.lastTokenBuffer).isSameAs(tokenBuffer);
    }

    private static final class FixedTokenStream implements TokenStream {
        @Override
        public Token nextToken() throws TokenStreamException {
            return new CommonToken(Token.EOF_TYPE, "<EOF>");
        }
    }
}
