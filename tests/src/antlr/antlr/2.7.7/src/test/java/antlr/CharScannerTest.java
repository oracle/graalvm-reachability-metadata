/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharScannerTest {
    @Test
    void nextTokenInstantiatesTheConfiguredTokenClass() throws TokenStreamException {
        ExposedCharScanner scanner = new ExposedCharScanner();
        scanner.setTokenObjectClass(CommonToken.class.getName());
        scanner.setLine(7);
        scanner.setColumn(11);

        Token token = scanner.nextToken();

        assertThat(token).isInstanceOf(CommonToken.class);
        assertThat(token.getType()).isEqualTo(123);
        assertThat(token.getLine()).isEqualTo(7);
        assertThat(token.getColumn()).isEqualTo(11);
    }

    static final class ExposedCharScanner extends CharScanner {
        ExposedCharScanner() {
            super(new LexerSharedInputState(new StringReader("token")));
        }

        @Override
        public Token nextToken() throws TokenStreamException {
            resetText();
            return makeToken(123);
        }
    }
}
