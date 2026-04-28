/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xpp3.xpp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;

import org.junit.jupiter.api.Test;
import org.xmlpull.mxp1.MXParserCachingStrings;
import org.xmlpull.v1.XmlPullParser;

public class MXParserCachingStringsTest {
    @Test
    void cloneUsesCloneableReaderAndParsesFromClonedInput() throws Exception {
        MXParserCachingStrings parser = new MXParserCachingStrings();
        parser.setInput(new CloneableReader("<root><child>value</child></root>"));

        MXParserCachingStrings clonedParser = (MXParserCachingStrings) parser.clone();

        assertThat(nextStartTagName(parser)).isEqualTo("root");
        assertThat(nextStartTagName(clonedParser)).isEqualTo("root");
    }

    private static String nextStartTagName(XmlPullParser parser) throws Exception {
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        return parser.getName();
    }

    public static class CloneableReader extends Reader implements Cloneable {
        private final String source;
        private int offset;

        CloneableReader(String source) {
            this.source = source;
        }

        @Override
        public int read(char[] buffer, int bufferOffset, int length) {
            if (offset >= source.length()) {
                return -1;
            }

            int charsToRead = Math.min(length, source.length() - offset);
            source.getChars(offset, offset + charsToRead, buffer, bufferOffset);
            offset += charsToRead;
            return charsToRead;
        }

        @Override
        public void close() throws IOException {
            offset = source.length();
        }

        @Override
        public CloneableReader clone() {
            CloneableReader clone = new CloneableReader(source);
            clone.offset = offset;
            return clone;
        }
    }
}
