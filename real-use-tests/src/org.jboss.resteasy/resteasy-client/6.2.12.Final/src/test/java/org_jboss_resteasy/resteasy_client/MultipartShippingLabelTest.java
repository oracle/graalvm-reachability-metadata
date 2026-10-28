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
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's multipart entity reader subsystem to retrieve a shipping manifest and binary label.
public class MultipartShippingLabelTest {
    private static final String BOUNDARY = "shipping-package-42";
    private static final String MANIFEST = """
            shipment=42
            recipient=Ren\u00e9e Martin
            label=shipment-42.raw
            """;
    private static final byte[] LABEL = {
        0, 0x7f, (byte) 0x80, (byte) 0xff, 13, 10, 0x55, (byte) 0xaa
    };

    @Test
    @Timeout(60)
    public void extractsTheManifestAndPreservesEveryBinaryLabelByte() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/shipments/42/print-package", MultipartShippingLabelTest::servePrintPackage);
        server.start();
        try {
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            try (ResteasyClient client = builder.build();
                    Response response = client.target("http://127.0.0.1:" + server.getAddress().getPort())
                            .path("shipments/42/print-package").request("multipart/mixed").get()) {
                assertEquals(200, response.getStatus());
                assertEquals("mixed", response.getMediaType().getSubtype());
                MultipartInput shipment = response.readEntity(MultipartInput.class);
                try {
                    List<InputPart> parts = shipment.getParts();
                    assertEquals(2, parts.size());
                    InputPart manifest = parts.get(0);
                    assertEquals("<manifest>", manifest.getHeaders().getFirst("Content-ID"));
                    assertEquals(MediaType.TEXT_PLAIN_TYPE.withCharset("UTF-8"), manifest.getMediaType());
                    assertEquals(MANIFEST, manifest.getBodyAsString());

                    InputPart label = parts.get(1);
                    assertEquals("<label>", label.getHeaders().getFirst("Content-ID"));
                    assertEquals("shipment-42.raw", label.getFileName());
                    assertEquals(MediaType.APPLICATION_OCTET_STREAM_TYPE, label.getMediaType());
                    assertArrayEquals(LABEL, label.getBody(byte[].class, null));
                } finally {
                    shipment.close();
                }
            }
        } finally {
            server.stop(0);
        }
    }

    private static void servePrintPackage(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())
                    || !"multipart/mixed".equals(exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT))) {
                exchange.sendResponseHeaders(406, -1);
                return;
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + BOUNDARY + "\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-ID: <manifest>\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            body.write(MANIFEST.getBytes(StandardCharsets.UTF_8));
            body.write(("\r\n--" + BOUNDARY + "\r\n"
                    + "Content-Type: application/octet-stream\r\n"
                    + "Content-ID: <label>\r\n"
                    + "Content-Disposition: attachment; filename=\"shipment-42.raw\"\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            body.write(LABEL);
            body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            byte[] bytes = body.toByteArray();
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE,
                    "multipart/mixed; boundary=\"" + BOUNDARY + "\"");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
