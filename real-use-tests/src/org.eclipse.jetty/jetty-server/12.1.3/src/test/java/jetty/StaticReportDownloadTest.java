/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises Jetty's static resource serving subsystem for cacheable, resumable report downloads.
public class StaticReportDownloadTest {

    @Test
    @Timeout(60)
    public void downloadsReportWithCacheValidationAndByteRanges(@TempDir Path directory) throws Exception {
        String report = """
                invoice,customer,total
                INV-101,Acme,125.00
                INV-102,Northwind,80.00
                """;
        Files.writeString(directory.resolve("invoices.txt"), report);

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);

        ResourceHandler resources = new ResourceHandler();
        resources.setBaseResource(ResourceFactory.of(server).newResource(directory));
        resources.setDirAllowed(false);
        resources.setEtags(true);
        resources.setAcceptRanges(true);
        resources.setCacheControl("public, max-age=3600");
        server.setHandler(resources);

        try {
            server.start();
            URI download = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/invoices.txt");
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> full = client.send(
                        HttpRequest.newBuilder(download).timeout(Duration.ofSeconds(10)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, full.statusCode());
                assertEquals(report, full.body());
                assertEquals("public, max-age=3600", full.headers().firstValue("Cache-Control").orElseThrow());
                assertEquals("bytes", full.headers().firstValue("Accept-Ranges").orElseThrow());
                String etag = full.headers().firstValue("ETag").orElseThrow();
                assertFalse(etag.isBlank());

                HttpResponse<String> cached = client.send(
                        HttpRequest.newBuilder(download).timeout(Duration.ofSeconds(10))
                                .header("If-None-Match", etag).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(304, cached.statusCode());
                assertEquals("", cached.body());
                assertEquals(etag, cached.headers().firstValue("ETag").orElseThrow());

                HttpResponse<String> partial = client.send(
                        HttpRequest.newBuilder(download).timeout(Duration.ofSeconds(10))
                                .header("Range", "bytes=0-7").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(206, partial.statusCode());
                assertEquals("invoice,", partial.body());
                assertEquals("bytes 0-7/" + report.length(),
                        partial.headers().firstValue("Content-Range").orElseThrow());
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }
}
