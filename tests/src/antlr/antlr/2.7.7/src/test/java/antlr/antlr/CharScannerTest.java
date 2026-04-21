/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.CharBuffer;
import antlr.CharScanner;
import antlr.Token;
import antlr.TokenStreamException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class CharScannerTest {

    @Test
    void makeTokenInstantiatesConfiguredTokenType() {
        ExposedCharScanner scanner = new ExposedCharScanner();
        scanner.setLine(4);
        scanner.setColumn(7);
        scanner.resetText();
        scanner.setTokenObjectClass(TrackingToken.class.getName());

        Token token = scanner.createToken(42);

        assertThat(token).isInstanceOf(TrackingToken.class);
        assertThat(token.getType()).isEqualTo(42);
        assertThat(token.getLine()).isEqualTo(4);
        assertThat(token.getColumn()).isEqualTo(7);
    }

    public static final class TrackingToken extends Token {
        private int line;
        private int column;

        public TrackingToken() {
        }

        @Override
        public int getLine() {
            return line;
        }

        @Override
        public int getColumn() {
            return column;
        }

        @Override
        public void setLine(int line) {
            this.line = line;
        }

        @Override
        public void setColumn(int column) {
            this.column = column;
        }
    }

    private static final class ExposedCharScanner extends CharScanner {
        private ExposedCharScanner() {
            super(new CharBuffer(new StringReader("antlr")));
        }

        private Token createToken(int type) {
            return makeToken(type);
        }

        @Override
        public Token nextToken() throws TokenStreamException {
            return Token.badToken;
        }
    }
}
