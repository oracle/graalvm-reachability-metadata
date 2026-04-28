/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpUtils;

import org.junit.jupiter.api.Test;

@SuppressWarnings({"deprecation", "unchecked"})
public class HttpUtilsTest {
    @Test
    void parseQueryStringDecodesNamesAndCollectsRepeatedParameters() {
        final Hashtable<String, String[]> parameters = HttpUtils.parseQueryString(
                "name=Jetty+Servlet&name=API&encoded=%2Froot%2Bleaf&empty=");

        assertThat(parameters).containsOnlyKeys("name", "encoded", "empty");
        assertThat(parameters.get("name")).containsExactly("Jetty Servlet", "API");
        assertThat(parameters.get("encoded")).containsExactly("/root+leaf");
        assertThat(parameters.get("empty")).containsExactly("");
    }

    @Test
    void parsePostDataReadsTheExpectedNumberOfBytes() {
        final byte[] formData = "mode=compact&mode=expanded".getBytes(StandardCharsets.ISO_8859_1);

        final Hashtable<String, String[]> parameters = HttpUtils.parsePostData(
                formData.length,
                new ByteArrayServletInputStream(formData));

        assertThat(parameters.get("mode")).containsExactly("compact", "expanded");
    }

    @Test
    void parsePostDataReportsShortReadsWithLocalizedMessage() {
        final byte[] formData = "name=value".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> HttpUtils.parsePostData(
                formData.length + 1,
                new ByteArrayServletInputStream(formData)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Short Read");
    }

    private static final class ByteArrayServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        private ByteArrayServletInputStream(final byte[] bytes) {
            this.delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }
    }
}
