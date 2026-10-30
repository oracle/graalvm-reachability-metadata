/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import com.sun.net.httpserver.HttpServer;
import org.apache.calcite.avatica.ConnectionConfigImpl;
import org.apache.calcite.avatica.remote.AvaticaHttpClient;
import org.apache.calcite.avatica.remote.AvaticaHttpClientFactoryImpl;
import org.apache.calcite.avatica.remote.AvaticaHttpClientImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class AvaticaHttpClientFactoryImplTest {
    @Test
    @Timeout(30)
    void constructsConfiguredClientAndExchangesAnHttpPayload() throws Exception {
        byte[] request = "select current_schema".getBytes(StandardCharsets.UTF_8);
        byte[] response = "sales".getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> received = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/avatica", exchange -> {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                received.set(exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            Properties properties = new Properties();
            properties.setProperty("httpclient_impl", AvaticaHttpClientImpl.class.getName());
            URI endpoint = new URI("http", null, "127.0.0.1", server.getAddress().getPort(), "/avatica", null, null);
            AvaticaHttpClient client = AvaticaHttpClientFactoryImpl.getInstance().getClient(
                    endpoint.toURL(), new ConnectionConfigImpl(properties), null);
            assertInstanceOf(AvaticaHttpClientImpl.class, client);
            assertArrayEquals(response, client.send(request));
            assertArrayEquals(request, received.get());
            assertEquals("POST", method.get());
        } finally {
            server.stop(0);
        }
    }
}
