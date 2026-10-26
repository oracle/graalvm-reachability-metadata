/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Variant;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.interceptors.AcceptEncodingGZIPFilter;
import org.jboss.resteasy.plugins.interceptors.GZIPDecodingInterceptor;
import org.jboss.resteasy.plugins.interceptors.GZIPEncodingInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's gzip entity interceptor subsystem to upload telemetry and read a compressed receipt.
public class CompressedTelemetryUploadTest {
    private static final String READINGS = """
            sensor,temperature
            freezer-north,-18.5
            freezer-south,-19.0
            """;
    private static final String RECEIPT = """
            batch=delivery-42
            accepted=2
            sensors=freezer-north,freezer-south
            """;

    @Test
    @Timeout(60)
    public void compressesTheUploadAndDecodesTheIngestionReceipt() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/telemetry/delivery-42", CompressedTelemetryUploadTest::ingestTelemetry);
        server.start();
        try {
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            builder.register(new AcceptEncodingGZIPFilter());
            builder.register(new GZIPEncodingInterceptor());
            builder.register(new GZIPDecodingInterceptor());
            Variant compressedCsv = new Variant(MediaType.valueOf("text/csv; charset=UTF-8"),
                    (Locale) null, "gzip");
            try (ResteasyClient client = builder.build();
                    Response response = client.target("http://127.0.0.1:" + server.getAddress().getPort())
                            .path("telemetry/delivery-42").request(MediaType.TEXT_PLAIN_TYPE)
                            .post(Entity.entity(READINGS, compressedCsv))) {
                assertEquals(201, response.getStatus());
                assertEquals("delivery-42", response.getHeaderString("X-Ingestion-Batch"));
                assertEquals(RECEIPT, response.readEntity(String.class));
            }
        } finally {
            server.stop(0);
        }
    }

    private static void ingestTelemetry(HttpExchange exchange) throws IOException {
        try (exchange) {
            String acceptEncoding = exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT_ENCODING);
            String contentType = exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            if (!"POST".equals(exchange.getRequestMethod())
                    || !"gzip".equals(exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_ENCODING))
                    || acceptEncoding == null || !acceptEncoding.contains("gzip")
                    || contentType == null || !MediaType.valueOf("text/csv")
                            .isCompatible(MediaType.valueOf(contentType))) {
                exchange.sendResponseHeaders(415, -1);
                return;
            }
            String readings;
            try (GZIPInputStream input = new GZIPInputStream(exchange.getRequestBody())) {
                readings = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!READINGS.equals(readings)) {
                exchange.sendResponseHeaders(422, -1);
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (GZIPOutputStream output = new GZIPOutputStream(buffer)) {
                output.write(RECEIPT.getBytes(StandardCharsets.UTF_8));
            }
            byte[] compressedReceipt = buffer.toByteArray();
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_ENCODING, "gzip");
            exchange.getResponseHeaders().set("X-Ingestion-Batch", "delivery-42");
            exchange.sendResponseHeaders(201, compressedReceipt.length);
            exchange.getResponseBody().write(compressedReceipt);
        }
    }
}
