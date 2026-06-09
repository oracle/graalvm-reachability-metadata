/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContentParserInner_configureXmlSlurper_closure1Test {
    @Test
    void appliesConfiguredXmlParserPropertyWhenParsingXmlResponse() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/document", this::sendXmlResponse);
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .config(config().xmlConfig(xmlConfig().property(XMLConstants.ACCESS_EXTERNAL_DTD, "")))
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/document")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .body("document.title", equalTo("Configured XML"));
            String dynamicallyLoadedClassName = "io.restassured.internal.support.FileReader";
            assertEquals(dynamicallyLoadedClassName, invokeGeneratedClassLookup(dynamicallyLoadedClassName).getName());
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = ContentParserInner_configureXmlSlurper_closure1Test.class
                .getClassLoader()
                .loadClass("io.restassured.internal.ContentParser$_configureXmlSlurper_closure1");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private void sendXmlResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "application/xml; charset=UTF-8");
            byte[] body = """
                    <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                    <document><title>Configured XML</title></document>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
