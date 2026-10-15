/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import org.apache.pulsar.client.api.url.PulsarURLStreamHandlerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class PulsarURLStreamHandlerFactoryTest {
    @Test
    void createsADataHandlerThatDecodesInlineContent() throws Exception {
        PulsarURLStreamHandlerFactory factory = new PulsarURLStreamHandlerFactory();
        URLStreamHandler handler = factory.createURLStreamHandler("data");
        URL url = new URL(null, "data:text/plain;base64,cHVsc2Fy", handler);
        URLConnection connection = url.openConnection();

        byte[] content;
        try (InputStream input = connection.getInputStream()) {
            content = input.readAllBytes();
        }

        assertThat(handler).isNotNull();
        assertThat(connection.getContentType()).isEqualTo("text/plain");
        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("pulsar");
        assertThat(factory.createURLStreamHandler("unknown")).isNull();
    }
}
