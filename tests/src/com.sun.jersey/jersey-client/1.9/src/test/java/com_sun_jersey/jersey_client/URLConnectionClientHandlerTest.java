/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class URLConnectionClientHandlerTest {
    private static final String LOOPBACK_HOST = "127.0.0.1";

    @Test
    void usesReflectionWorkaroundForUnsupportedHttpMethods() throws Exception {
        AtomicReference<String> receivedMethod = new AtomicReference<>();
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), 0);
        HttpServer server = HttpServer.create(address, 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/resource", exchange -> handle(exchange, receivedMethod));
        server.start();

        try {
            URI uri = URI.create("http://" + LOOPBACK_HOST
                    + ":" + server.getAddress().getPort() + "/resource");
            ClientRequest request = ClientRequest.create().build(uri, "PROPFIND");
            request.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, 5000);
            request.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, 5000);
            request.getProperties().put(
                    URLConnectionClientHandler.PROPERTY_HTTP_URL_CONNECTION_SET_METHOD_WORKAROUND,
                    Boolean.TRUE);

            URLConnectionClientHandler handler = new URLConnectionClientHandler();
            ClientResponse response = handler.handle(request);
            try {
                assertThat(response.getStatus()).isEqualTo(204);
            } finally {
                if (response.getEntityInputStream() != null) {
                    response.close();
                }
            }

            assertThat(receivedMethod.get()).isEqualTo("PROPFIND");
        } finally {
            server.stop(0);
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void handle(HttpExchange exchange, AtomicReference<String> receivedMethod) throws IOException {
        receivedMethod.set(exchange.getRequestMethod());
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
