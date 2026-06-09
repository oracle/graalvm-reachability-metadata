/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class TrustAndKeystoreSpecImplInner_createStore_closure1Test {
    private static final String KEY_STORE_RESOURCE = "/ssl-config-test-keystore.p12";
    private static final String KEY_STORE_PASSWORD = "changeit";
    private static final String KEY_STORE_TYPE = "PKCS12";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void fileBackedSslStoresAreLoadedWhenApplyingSslConfig() throws Exception {
        Path storePath = copyKeyStoreToTemporaryFile();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(createServerSslContext()));
        server.createContext(
                "/secure", TrustAndKeystoreSpecImplInner_createStore_closure1Test::sendSecureResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            SSLConfig sslConfig = SSLConfig.sslConfig()
                    .keyStore(storePath.toFile(), KEY_STORE_PASSWORD)
                    .trustStore(storePath.toFile(), KEY_STORE_PASSWORD)
                    .keystoreType(KEY_STORE_TYPE)
                    .trustStoreType(KEY_STORE_TYPE)
                    .allowAllHostnames();

            given()
                    .config(config().sslConfig(sslConfig))
                    .baseUri("https://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/secure")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .body(equalTo("ssl stores applied"));
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Path copyKeyStoreToTemporaryFile() throws IOException {
        Path storePath = temporaryDirectory.resolve("ssl-config-test-keystore.p12");
        try (InputStream inputStream = TrustAndKeystoreSpecImplInner_createStore_closure1Test.class
                .getResourceAsStream(KEY_STORE_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("Missing test keystore resource: " + KEY_STORE_RESOURCE);
            }
            Files.copy(inputStream, storePath);
        }
        return storePath;
    }

    private static SSLContext createServerSslContext() throws Exception {
        KeyStore keyStore = loadTestKeyStore();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, KEY_STORE_PASSWORD.toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    private static KeyStore loadTestKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
        try (InputStream inputStream = TrustAndKeystoreSpecImplInner_createStore_closure1Test.class
                .getResourceAsStream(KEY_STORE_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("Missing test keystore resource: " + KEY_STORE_RESOURCE);
            }
            keyStore.load(inputStream, KEY_STORE_PASSWORD.toCharArray());
        }
        return keyStore;
    }

    private static void sendSecureResponse(HttpExchange exchange) throws IOException {
        try {
            byte[] body = "ssl stores applied".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
