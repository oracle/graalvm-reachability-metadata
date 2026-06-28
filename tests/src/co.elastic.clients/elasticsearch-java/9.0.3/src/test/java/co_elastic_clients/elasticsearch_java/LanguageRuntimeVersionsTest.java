/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.RequestOptions;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class LanguageRuntimeVersionsTest {
    @Test
    void rest5ClientSendsDetectedLanguageRuntimeVersionsInMetaHeader() throws IOException {
        AtomicReference<String> clientMetaHeader = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        try {
            server.createContext("/", exchange -> sendOkResponse(exchange, clientMetaHeader));
            server.setExecutor(executor);
            server.start();

            try (Rest5Client client = Rest5Client.builder(serverUri(server)).build()) {
                Request request = new Request("GET", "/");
                request.setOptions(RequestOptions.DEFAULT.toBuilder().setRequestConfig(requestConfig()).build());

                Response response = client.performRequest(request);

                assertThat(response.getStatusCode()).isEqualTo(200);
                assertThat(clientMetaHeader.get())
                    .containsPattern("(^|,)kt=\\d+\\.\\d+($|,)")
                    .containsPattern("(^|,)sc=\\d+\\.\\d+($|,)");
            }
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static RequestConfig requestConfig() {
        return RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(10))
            .setConnectionRequestTimeout(Timeout.ofSeconds(10))
            .setResponseTimeout(Timeout.ofSeconds(10))
            .build();
    }

    private static URI serverUri(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void sendOkResponse(HttpExchange exchange, AtomicReference<String> clientMetaHeader)
        throws IOException {
        clientMetaHeader.set(exchange.getRequestHeaders().getFirst("X-Elastic-Client-Meta"));
        byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
    }
}
