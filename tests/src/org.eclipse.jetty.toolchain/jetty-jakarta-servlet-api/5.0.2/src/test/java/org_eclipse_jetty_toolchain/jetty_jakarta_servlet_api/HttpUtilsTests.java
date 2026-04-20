/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_toolchain.jetty_jakarta_servlet_api;

import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class HttpUtilsTests {
    @Test
    void parseQueryStringDecodesRepeatedParameters() {
        Hashtable<String, String[]> parameters =
                HttpUtils.parseQueryString("name=Jane+Doe&city=New%20York&city=Boston");

        assertThat(parameters).containsOnlyKeys("name", "city");
        assertThat(parameters.get("name")).containsExactly("Jane Doe");
        assertThat(parameters.get("city")).containsExactly("New York", "Boston");
    }

    @Test
    void parsePostDataRejectsShortReadsWithLocalizedMessage() {
        ServletInputStream inputStream =
                new ByteArrayServletInputStream("a=1".getBytes(StandardCharsets.ISO_8859_1));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpUtils.parsePostData(4, inputStream))
                .withMessage("Short Read");
    }

    private static final class ByteArrayServletInputStream extends ServletInputStream {
        private final byte[] bytes;
        private int index;

        private ByteArrayServletInputStream(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int read() {
            if (index >= bytes.length) {
                return -1;
            }
            int value = bytes[index] & 0xff;
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
        public void setReadListener(ReadListener readListener) {
        }
    }
}
