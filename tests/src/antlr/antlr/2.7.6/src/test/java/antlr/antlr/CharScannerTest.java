/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.CharBuffer;
import antlr.CharScanner;
import antlr.CommonToken;
import antlr.LexerSharedInputState;
import antlr.Token;
import antlr.TokenStreamException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CharScannerTest {
    private static final int TOKEN_TYPE = 42;

    @Test
    void makeTokenCreatesConfiguredCommonTokenWithCurrentStartPosition() {
        ExposedCharScanner scanner = new ExposedCharScanner();
        scanner.setLine(12);
        scanner.setColumn(7);
        scanner.resetText();

        Token token = scanner.createToken(TOKEN_TYPE);

        assertThat(token).isExactlyInstanceOf(CommonToken.class);
        assertThat(token.getType()).isEqualTo(TOKEN_TYPE);
        assertThat(token.getLine()).isEqualTo(12);
        assertThat(token.getColumn()).isEqualTo(7);
    }

    private static final class ExposedCharScanner extends CharScanner {
        private ExposedCharScanner() {
            super(new LexerSharedInputState(new CharBuffer(new StringReader("token input"))));
        }

        @Override
        public Token nextToken() throws TokenStreamException {
            return Token.badToken;
        }

        private Token createToken(int tokenType) {
            return makeToken(tokenType);
        }
    }
}
