/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import org.apache.pulsar.client.api.url.PulsarURLStreamHandlerFactory;
import org.junit.jupiter.api.Test;

public class PulsarURLStreamHandlerFactoryTest {
    @Test
    void createsDataProtocolHandlerAndOpensDataUrl() throws Exception {
        PulsarURLStreamHandlerFactory factory = new PulsarURLStreamHandlerFactory();

        URLStreamHandler handler = factory.createURLStreamHandler("data");

        assertThat(handler).isNotNull();
        URL url = new URL(null, "data:text/plain;base64,SGVsbG8sIFB1bHNhciE=", handler);
        URLConnection connection = url.openConnection();
        assertThat(connection.getContentType()).isEqualTo("text/plain");
        assertThat(connection.getContentEncoding()).isEqualTo("identity");
        assertThat(connection.getContentLengthLong()).isEqualTo("Hello, Pulsar!".length());
        try (InputStream input = connection.getInputStream()) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("Hello, Pulsar!");
        }
    }

    @Test
    void returnsNoHandlerForUnknownProtocol() {
        PulsarURLStreamHandlerFactory factory = new PulsarURLStreamHandlerFactory();

        assertThat(factory.createURLStreamHandler("unknown")).isNull();
    }
}
