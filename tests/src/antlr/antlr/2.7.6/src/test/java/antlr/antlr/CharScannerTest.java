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
import antlr.Token;
import antlr.TokenStreamException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class CharScannerTest {
    @Test
    void nextTokenCreatesDefaultCommonToken() throws Exception {
        SingleTokenScanner scanner = new SingleTokenScanner();
        scanner.setLine(7);
        scanner.setColumn(13);

        Token token = scanner.nextToken();

        assertThat(token).isInstanceOf(CommonToken.class);
        assertThat(token.getType()).isEqualTo(SingleTokenScanner.WORD_TYPE);
        assertThat(token.getLine()).isEqualTo(7);
        assertThat(token.getColumn()).isEqualTo(13);
    }

    private static final class SingleTokenScanner extends CharScanner {
        private static final int WORD_TYPE = 101;

        private SingleTokenScanner() {
            super(new CharBuffer(new StringReader("")));
        }

        @Override
        public Token nextToken() throws TokenStreamException {
            resetText();
            return makeToken(WORD_TYPE);
        }
    }
}
