/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

final class TrinoProtocolServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Path trustStore;
    private final boolean secure;
    final List<String> statements = new CopyOnWriteArrayList<>();
    final List<String> users = new CopyOnWriteArrayList<>();

    TrinoProtocolServer(boolean secure) throws Exception {
        this(secure, new String[0]);
    }

    TrinoProtocolServer(boolean secure, String... protocols) throws Exception {
        this.secure = secure;
        if (secure) {
            trustStore = Files.createTempFile("trino-test-trust-", ".p12");
            try (InputStream input = TrinoProtocolServer.class.getResourceAsStream("localhost.p12")) {
                Files.copy(input, trustStore, StandardCopyOption.REPLACE_EXISTING);
            }
            KeyStore keys = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(trustStore)) {
                keys.load(input, "changeit".toCharArray());
            }
            KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keys, "changeit".toCharArray());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, null);
            HttpsServer https = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(context) {
                @Override
                public void configure(HttpsParameters parameters) {
                    SSLParameters sslParameters = getSSLContext().getDefaultSSLParameters();
                    if (protocols.length > 0) {
                        sslParameters.setProtocols(protocols);
                    }
                    parameters.setSSLParameters(sslParameters);
                }
            });
            server = https;
        } else {
            trustStore = null;
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        }
        server.createContext("/v1/statement", this::statement);
        server.createContext("/next", exchange -> respond(exchange, false));
        server.setExecutor(executor);
        server.start();
    }

    String jdbcUrl() {
        return "jdbc:trino://localhost:" + server.getAddress().getPort() + "/memory/default";
    }

    Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user", "metadata-user");
        if (secure) {
            properties.setProperty("SSL", "true");
            properties.setProperty("SSLTrustStorePath", trustStore.toString());
            properties.setProperty("SSLTrustStorePassword", "changeit");
            properties.setProperty("SSLTrustStoreType", "PKCS12");
        }
        return properties;
    }

    private void statement(HttpExchange exchange) throws IOException {
        statements.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        users.add(exchange.getRequestHeaders().getFirst("X-Trino-User"));
        respond(exchange, true);
    }

    private void respond(HttpExchange exchange, boolean first) throws IOException {
        String base = (secure ? "https" : "http") + "://localhost:" + server.getAddress().getPort();
        String next = first ? "\"nextUri\":\"" + base + "/next\"," : "";
        String data = first ? "[42,\"hello\",\"2020-06-15\"]" : "[43,null,\"2020-06-16\"]";
        String json = """
                {
                  "id":"metadata_query",
                  "infoUri":"%s/query",
                  %s
                  "columns":[
                    {"name":"number","type":"bigint","typeSignature":{"rawType":"bigint","arguments":[]}},
                    {"name":"text","type":"varchar","typeSignature":{"rawType":"varchar","arguments":[{"kind":"LONG","value":2147483647}]}},
                    {"name":"day","type":"date","typeSignature":{"rawType":"date","arguments":[]}}
                  ],
                  "data":[%s],
                  "stats":{"state":"%s","queued":false,"scheduled":true,"nodes":1,"totalSplits":1,"completedSplits":1},
                  "warnings":[]
                }
                """.formatted(base, next, data, first ? "RUNNING" : "FINISHED");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    @Override
    public void close() throws IOException {
        server.stop(0);
        executor.shutdownNow();
        if (trustStore != null) {
            Files.deleteIfExists(trustStore);
        }
    }
}
