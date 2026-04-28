/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_servlet;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpUtils;

import org.junit.jupiter.api.Test;

public class HttpUtilsTests {
    @Test
    void parsePostDataRejectsShortReadsWithLocalizedMessage() {
        ServletInputStream inputStream = new ByteArrayServletInputStream("a=1".getBytes(StandardCharsets.ISO_8859_1));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpUtils.parsePostData(4, inputStream))
                .withMessage("Short Read");
    }

    static final class ByteArrayServletInputStream extends ServletInputStream {
        private final byte[] bytes;
        private int index;

        ByteArrayServletInputStream(byte[] bytes) {
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

    }
}
