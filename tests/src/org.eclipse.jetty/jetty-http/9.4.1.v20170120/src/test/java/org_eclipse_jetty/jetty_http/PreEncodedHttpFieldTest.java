/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PreEncodedHttpFieldTest {
    @Test
    void encodesHttpOneHeaderUsingDiscoveredEncoder() {
        PreEncodedHttpField field = new PreEncodedHttpField(HttpHeader.CONTENT_TYPE, "text/plain");
        ByteBuffer output = ByteBuffer.allocate(64);

        field.putTo(output, HttpVersion.HTTP_1_1);
        output.flip();

        assertThat(StandardCharsets.ISO_8859_1.decode(output).toString())
                .isEqualTo("Content-Type: text/plain\r\n");
    }
}
