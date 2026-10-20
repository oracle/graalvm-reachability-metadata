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

import org.apache.pulsar.client.api.url.PulsarURLStreamHandlerFactory;
import org.junit.jupiter.api.Test;

public class PulsarURLStreamHandlerFactoryTest {
    @Test
    void createsHandlerThatDecodesDataUrls() throws Exception {
        PulsarURLStreamHandlerFactory factory = new PulsarURLStreamHandlerFactory();
        URLStreamHandler handler = factory.createURLStreamHandler("data");
        URL url = new URL(null, "data:text/plain,hello%20pulsar", handler);
        URLConnection connection = url.openConnection();

        assertThat(handler).isNotNull();
        assertThat(connection.getContentType()).isEqualTo("text/plain");
        try (InputStream input = connection.getInputStream()) {
            assertThat(input).hasContent("hello pulsar");
        }
        assertThat(factory.createURLStreamHandler("http")).isNull();
    }
}
