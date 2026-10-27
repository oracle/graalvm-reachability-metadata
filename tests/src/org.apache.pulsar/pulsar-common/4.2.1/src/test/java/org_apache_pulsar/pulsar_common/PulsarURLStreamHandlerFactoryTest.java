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
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

import org.apache.pulsar.client.api.url.DataURLStreamHandler;
import org.apache.pulsar.client.api.url.PulsarURLStreamHandlerFactory;
import org.junit.jupiter.api.Test;

public class PulsarURLStreamHandlerFactoryTest {
    @Test
    void createsDataUrlHandlerAndReadsDataUrlContent() throws Exception {
        final PulsarURLStreamHandlerFactory factory = new PulsarURLStreamHandlerFactory();
        final URLStreamHandler handler = factory.createURLStreamHandler("data");

        assertThat(handler).isInstanceOf(DataURLStreamHandler.class);

        final URL url = new URL(null, "data:text/plain;base64,cHVsc2Fy", handler);
        assertThat(url.openConnection().getContentType()).isEqualTo("text/plain");

        try (InputStream stream = url.openStream()) {
            final String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo("pulsar");
        }
    }

    @Test
    void returnsNullForUnsupportedProtocol() {
        final PulsarURLStreamHandlerFactory factory = new PulsarURLStreamHandlerFactory();

        assertThat(factory.createURLStreamHandler("pulsar")).isNull();
    }
}
