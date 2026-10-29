/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

final class OAuth2ClientTestSupport {

    private OAuth2ClientTestSupport() {
    }

    static ClientRegistration registration(String id) {
        return ClientRegistration.withRegistrationId(id)
                .clientId("orders-client")
                .clientSecret("orders-secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("https://issuer.example.test/token")
                .scope("orders.read", "orders.write")
                .build();
    }

    static RestClientClientCredentialsTokenResponseClient tokenClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .configureMessageConverters(converters -> {
                    converters.addCustomConverter(new FormHttpMessageConverter());
                    converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
        RestClientClientCredentialsTokenResponseClient client = new RestClientClientCredentialsTokenResponseClient();
        client.setRestClient(restClient);
        return client;
    }

    static final class TokenServer implements AutoCloseable {
        private final HttpServer server;
        private final BlockingQueue<TokenRequest> requests = new LinkedBlockingQueue<>();
        private volatile Reply reply = new Reply(200, """
                {"access_token":"first-token","token_type":"Bearer","expires_in":3600,"scope":"orders.read"}
                """);

        TokenServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/token", this::handle);
            this.server.start();
        }

        String tokenUri() {
            return "http://127.0.0.1:" + this.server.getAddress().getPort() + "/token";
        }

        void respond(int status, String body) {
            this.reply = new Reply(status, body);
        }

        TokenRequest request() throws InterruptedException {
            TokenRequest request = this.requests.poll(10, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            return request;
        }

        int pendingRequests() {
            return this.requests.size();
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                this.requests.add(new TokenRequest(exchange.getRequestMethod(),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        exchange.getRequestHeaders().getFirst("Authorization"), body));
                Reply currentReply = this.reply;
                byte[] response = currentReply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(currentReply.status(), response.length);
                exchange.getResponseBody().write(response);
            }
        }

        @Override
        public void close() {
            this.server.stop(0);
        }
    }

    record TokenRequest(String method, String contentType, String authorization, String body) {
        Map<String, String> parameters() {
            Map<String, String> parameters = new HashMap<>();
            for (String parameter : this.body.split("&")) {
                String[] pair = parameter.split("=", 2);
                parameters.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            return parameters;
        }
    }

    private record Reply(int status, String body) {
    }
}
