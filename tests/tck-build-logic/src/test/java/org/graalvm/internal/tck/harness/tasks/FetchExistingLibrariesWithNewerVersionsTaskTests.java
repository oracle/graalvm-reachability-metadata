/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FetchExistingLibrariesWithNewerVersionsTaskTests {

    @TempDir
    private Path tempDir;

    @Test
    void readMavenMetadataReturnsEmptyWhenMetadataIsMissing() {
        String missingMetadataUrl = tempDir.resolve("missing-maven-metadata.xml").toUri().toString();

        assertThat(FetchExistingLibrariesWithNewerVersionsTask.readMavenMetadata(missingMetadataUrl)).isEmpty();
    }

    @Test
    void readMavenMetadataReadsExistingMetadata() throws IOException {
        Path metadataFile = tempDir.resolve("maven-metadata.xml");
        Files.writeString(metadataFile, """
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);

        assertThat(FetchExistingLibrariesWithNewerVersionsTask.readMavenMetadata(metadataFile.toUri().toString()))
                .hasValueSatisfying(metadata -> assertThat(metadata).contains("<version>1.0.0</version>"));
    }

    @Test
    void readMavenMetadataRetriesRateLimitedRequests() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            if (requests.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "1");
                exchange.sendResponseHeaders(429, -1);
                exchange.close();
                return;
            }

            byte[] response = """
                    <metadata>
                      <versioning>
                        <versions>
                          <version>1.0.0</version>
                        </versions>
                      </versioning>
                    </metadata>
                    """.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        List<Long> retryDelays = new ArrayList<>();

        try {
            assertThat(FetchExistingLibrariesWithNewerVersionsTask.readMavenMetadata(
                    "http://localhost:" + server.getAddress().getPort() + "/maven-metadata.xml",
                    retryDelays::add))
                    .hasValueSatisfying(metadata -> assertThat(metadata).contains("<version>1.0.0</version>"));
        } finally {
            server.stop(0);
        }

        assertThat(requests).hasValue(2);
        assertThat(retryDelays).containsExactly(1_000L);
    }

    @Test
    void getNewerVersionsFromLibraryIndexKeepsVersionsAfterStartingVersion() {
        String metadata = """
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.0.0</version>
                      <version>1.1.0</version>
                      <version>1.2.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        List<String> newerVersions = FetchExistingLibrariesWithNewerVersionsTask.getNewerVersionsFromLibraryIndex(
                metadata, "1.0.0", "com.example:demo");

        assertThat(newerVersions).containsExactly("1.1.0", "1.2.0");
    }

    private HttpServer startServer(ThrowingExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    @FunctionalInterface
    private interface ThrowingExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
