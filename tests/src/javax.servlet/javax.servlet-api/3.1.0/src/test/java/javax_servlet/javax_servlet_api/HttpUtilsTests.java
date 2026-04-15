/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.javax_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpUtils;

import org.junit.jupiter.api.Test;

class HttpUtilsTests {
    @Test
    void parseQueryStringDecodesRepeatedParameters() {
        final Hashtable<String, String[]> parameters =
                HttpUtils.parseQueryString("name=Jane+Doe&city=New%20York&city=Boston");

        assertThat(parameters).containsOnlyKeys("name", "city");
        assertThat(parameters.get("name")).containsExactly("Jane Doe");
        assertThat(parameters.get("city")).containsExactly("New York", "Boston");
    }

    @Test
    void parsePostDataRejectsShortReadsWithLocalizedMessage() {
        final ServletInputStream inputStream =
                new ByteArrayServletInputStream("a=1".getBytes(StandardCharsets.ISO_8859_1));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpUtils.parsePostData(4, inputStream))
                .withMessage("Short Read");
    }

    private static final class ByteArrayServletInputStream extends ServletInputStream {
        private final byte[] bytes;
        private int index;
        private ByteArrayServletInputStream(final byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int read() {
            if (index >= bytes.length) {
                return -1;
            }
            final int value = bytes[index] & 0xff;
            index++;
            return value;
        }

        @Override
        public boolean isFinished() {
            return index >= bytes.length;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
        }
    }
}
