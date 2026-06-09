/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.XMLConstants;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.config.XmlConfig.xmlConfig;
import static org.hamcrest.Matchers.equalTo;

public class ContentParserInner_configureXmlSlurper_closure2Test {
    @Test
    void appliesConfiguredXmlParserFeatureWhenParsingXmlResponse() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/document", this::sendXmlResponse);
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .config(config().xmlConfig(xmlConfig().feature(XMLConstants.FEATURE_SECURE_PROCESSING, true)))
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/document")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .body("document.title", equalTo("Feature Configured XML"));
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendXmlResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "application/xml; charset=UTF-8");
            byte[] body = """
                    <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                    <document><title>Feature Configured XML</title></document>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
