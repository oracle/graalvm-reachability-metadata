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

    /// Maven Central's `org.aspectj:aspectjweaver` listing puts `1.9.21` after `1.9.21.2`; testing
    /// it against the `1.9.21.2` suite filed failure issues for a version older than `latest`.
    @Test
    void getNewerVersionsFromLibraryIndexDropsOlderVersionsListedAfterStartingVersion() {
        String metadata = """
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.9.21.1</version>
                      <version>1.9.21.2</version>
                      <version>1.9.21.M1</version>
                      <version>1.9.21</version>
                      <version>1.9.22.1</version>
                      <version>1.9.22</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        List<String> newerVersions = FetchExistingLibrariesWithNewerVersionsTask.getNewerVersionsFromLibraryIndex(
                metadata, "1.9.21.2", "org.aspectj:aspectjweaver");

        assertThat(newerVersions).containsExactly("1.9.22.1", "1.9.22");
    }

    /// `com.graphql-java:java-dataloader` publishes date-stamped builds out of order.
    @Test
    void getNewerVersionsFromLibraryIndexOrdersDateStampedVersions() {
        String metadata = """
                <metadata>
                  <versioning>
                    <versions>
                      <version>6.0.0</version>
                      <version>2021-10-29T01-57-17-4253050</version>
                      <version>2021-03-30T02-06-56-9a47743</version>
                      <version>2021-08-19T04-04-25-efb3c9d</version>
                      <version>2022-02-15T03-28-59-388c4fd</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        List<String> newerVersions = FetchExistingLibrariesWithNewerVersionsTask.getNewerVersionsFromLibraryIndex(
                metadata, "2021-10-29T01-57-17-4253050", "com.graphql-java:java-dataloader");

        assertThat(newerVersions).containsExactly("2022-02-15T03-28-59-388c4fd");
    }

    @Test
    void versionOrderReadsAFourthNumberAsNewer() {
        List<String> versions = new ArrayList<>(List.of("1.9.21.2", "1.9.22", "1.9.21", "1.9.20.1", "1.9.21.1"));

        versions.sort(FetchExistingLibrariesWithNewerVersionsTask.VERSION_ORDER);

        assertThat(versions).containsExactly("1.9.20.1", "1.9.21", "1.9.21.1", "1.9.21.2", "1.9.22");
    }

    @Test
    void autoUpdateRequiresTheMarkerOnTheLatestEntry() throws IOException {
        Path disabledIndex = tempDir.resolve("disabled-index.json");
        Files.writeString(disabledIndex, """
                [
                  {
                    "latest": true,
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0"],
                    "allowed-packages": ["com.example"]
                  }
                ]
                """);
        Path staleMarkerIndex = tempDir.resolve("stale-marker-index.json");
        Files.writeString(staleMarkerIndex, """
                [
                  {
                    "auto-update": true,
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0"],
                    "allowed-packages": ["com.example"]
                  },
                  {
                    "latest": true,
                    "metadata-version": "2.0.0",
                    "tested-versions": ["2.0.0"],
                    "allowed-packages": ["com.example"]
                  }
                ]
                """);
        Path enabledIndex = tempDir.resolve("enabled-index.json");
        Files.writeString(enabledIndex, """
                [
                  {
                    "latest": true,
                    "auto-update": true,
                    "high-priority": true,
                    "metadata-version": "2.0.0",
                    "tested-versions": ["2.0.0"],
                    "allowed-packages": ["com.example"]
                  }
                ]
                """);

        assertThat(FetchExistingLibrariesWithNewerVersionsTask.isAutoUpdateEnabled(disabledIndex.toFile()))
                .isFalse();
        assertThat(FetchExistingLibrariesWithNewerVersionsTask.isAutoUpdateEnabled(staleMarkerIndex.toFile()))
                .isFalse();
        assertThat(FetchExistingLibrariesWithNewerVersionsTask.isAutoUpdateEnabled(enabledIndex.toFile()))
                .isTrue();
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
