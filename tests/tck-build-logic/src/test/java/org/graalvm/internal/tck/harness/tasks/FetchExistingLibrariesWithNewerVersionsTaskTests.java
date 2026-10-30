/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.gradle.api.logging.Logging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    /// Each row is a version and the next one in its release line, in the shapes Central serves:
    /// qualifiers, qualifier numbers past 9, release suffixes, build stamps and date stamps.
    @ParameterizedTest(name = "{0} < {1}")
    @CsvSource({
            "2.0.0-alpha1, 2.0.0-alpha2",
            "2.0.0-alpha9, 2.0.0-alpha10",
            "2.0.0-alpha2, 2.0.0-beta1",
            "2.0.0-beta1, 2.0.0",
            "6.0.0-M1, 6.0.0-M2",
            "6.0.0-M9, 6.0.0-M10",
            "6.0.0-M2, 6.0.0-RC1",
            "6.0.0-RC1, 6.0.0-RC2",
            "6.0.0-RC9, 6.0.0-RC10",
            "6.0.0-RC2, 6.0.0",
            "4.2.0.Alpha1, 4.2.0.Beta1",
            "4.2.0.Beta1, 4.2.0.RC4",
            "4.2.0.RC4, 4.2.0.Final",
            "6.4.0.CR1, 6.4.0.Final",
            "4.2.0.Final, 4.2.1.Final",
            "5.3.0.M1, 5.3.0.RC1",
            "5.3.0.RC1, 5.3.0.RELEASE",
            "1.9.21.M1, 1.9.21",
            "1.1.10.9, 1.1.10.12",
            "1.5.7-9, 1.5.7-19",
            "33.0.0-jre, 33.1.0-jre",
            "7.4.0.202509020913-r, 7.5.0.202512021534-r",
            "2021-08-19T04-04-25-efb3c9d, 2021-10-29T01-57-17-4253050",
    })
    void versionOrderRanksEachStepOfAReleaseLine(String older, String newer) {
        assertThat(FetchExistingLibrariesWithNewerVersionsTask.VERSION_ORDER.compare(older, newer)).isNegative();
        assertThat(FetchExistingLibrariesWithNewerVersionsTask.VERSION_ORDER.compare(newer, older)).isPositive();
    }

    /// `com.github.luben:zstd-jni` numbers its builds `1.5.7-9`, `1.5.7-10`, ...; read as text,
    /// `-10` sorts below `-9` and every later build was dropped.
    @Test
    void getNewerVersionsFromLibraryIndexComparesBuildNumbersAsNumbers() {
        String metadata = """
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.5.7-8</version>
                      <version>1.5.7-9</version>
                      <version>1.5.7-10</version>
                      <version>1.5.7-19</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        List<String> newerVersions = FetchExistingLibrariesWithNewerVersionsTask.getNewerVersionsFromLibraryIndex(
                metadata, "1.5.7-9", "com.github.luben:zstd-jni");

        assertThat(newerVersions).containsExactly("1.5.7-10", "1.5.7-19");
    }

    /// `org.eclipse.jgit:org.eclipse.jgit` puts a 12-digit build stamp in its fourth part, which
    /// overflows an `int`; one such version must not stop discovery for every library.
    @Test
    void getNewerVersionsFromLibraryIndexReadsBuildStampsWithoutOverflow() {
        String metadata = """
                <metadata>
                  <versioning>
                    <versions>
                      <version>7.4.0.202509020913-r</version>
                      <version>7.5.0.202512021534-r</version>
                      <version>7.6.0.202603022253-r</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        List<String> newerVersions = FetchExistingLibrariesWithNewerVersionsTask.getNewerVersionsFromLibraryIndex(
                metadata, "7.4.0.202509020913-r", "org.eclipse.jgit:org.eclipse.jgit");

        assertThat(newerVersions).containsExactly("7.5.0.202512021534-r", "7.6.0.202603022253-r");
    }

    /// A release published after its last candidate is newer, whichever suffix it carries.
    @Test
    void getNewerVersionsFromLibraryIndexKeepsTheReleaseAfterItsCandidates() {
        String metadata = """
                <metadata>
                  <versioning>
                    <versions>
                      <version>4.2.0.RC3</version>
                      <version>4.2.0.RC4</version>
                      <version>4.2.0.Final</version>
                      <version>4.2.1.Final</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        List<String> newerVersions = FetchExistingLibrariesWithNewerVersionsTask.getNewerVersionsFromLibraryIndex(
                metadata, "4.2.0.RC4", "io.netty:netty-common");

        assertThat(newerVersions).containsExactly("4.2.0.Final", "4.2.1.Final");
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

    /// `jakarta.enterprise:jakarta.enterprise.lang-model:5.0.0` ships only this POM. Recording it
    /// failed every daily run, and a build resolves the target anyway.
    private static final String RELOCATION_POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>jakarta.enterprise</groupId>
                    <artifactId>jakarta.enterprise.cdi-parent</artifactId>
                    <version>5.0.0</version>
                </parent>
                <artifactId>jakarta.enterprise.lang-model</artifactId>
                <packaging>pom</packaging>
                <distributionManagement>
                    <relocation>
                        <groupId>jakarta.cdi</groupId>
                        <artifactId>jakarta.cdi-lang-model-api</artifactId>
                    </relocation>
                </distributionManagement>
            </project>
            """;

    private static final String LIBRARY_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <groupId>jakarta.enterprise</groupId>
                <artifactId>jakarta.enterprise.lang-model</artifactId>
                <version>4.1.0</version>
                <build>
                    <plugins>
                        <plugin>
                            <artifactId>maven-shade-plugin</artifactId>
                            <configuration>
                                <relocations>
                                    <relocation>
                                        <pattern>org.example</pattern>
                                        <shadedPattern>shaded.org.example</shadedPattern>
                                    </relocation>
                                </relocations>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;

    @Test
    void pomRelocationReadsOnlyTheDistributionManagementRelocation() {
        assertThat(PomRelocation.target(
                RELOCATION_POM, "jakarta.enterprise", "jakarta.enterprise.lang-model", "5.0.0"))
                .hasValue("jakarta.cdi:jakarta.cdi-lang-model-api:5.0.0");
        assertThat(PomRelocation.target(
                LIBRARY_POM, "jakarta.enterprise", "jakarta.enterprise.lang-model", "4.1.0"))
                .isEmpty();
        assertThat(PomRelocation.target(
                "<project><distributionManagement><relocation><version>1.0</version></relocation>"
                        + "</distributionManagement></project>", "com.example", "demo", "1.0"))
                .isEmpty();
        assertThat(PomRelocation.target(
                "not a pom", "com.example", "demo", "1.0"))
                .isEmpty();
    }

    /// The relocated version leaves the candidates, the version past the cap is neither read nor
    /// offered, and a second run reads nothing again.
    @Test
    void dropRelocatedVersionsReadsOnlyTheVersionsThatCanEnterTheRun() throws IOException {
        List<String> requestedPaths = new CopyOnWriteArrayList<>();
        HttpServer server = startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestedPaths.add(path);
            String pom = path.contains("/5.0.0/") ? RELOCATION_POM : path.contains("/5.0.3/") ? null : LIBRARY_POM;
            if (pom == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                byte[] response = pom.getBytes();
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        String repositoryUrl = "http://localhost:" + server.getAddress().getPort() + "/maven2";
        List<String> candidates = List.of("5.0.0", "5.0.1", "5.0.3", "5.0.4");

        try {
            for (int run = 0; run < 2; run++) {
                assertThat(FetchExistingLibrariesWithNewerVersionsTask.dropRelocatedVersions(
                        repositoryUrl, "jakarta.enterprise:jakarta.enterprise.lang-model", candidates, 3,
                        Logging.getLogger(FetchExistingLibrariesWithNewerVersionsTaskTests.class)))
                        .containsExactly("5.0.1", "5.0.3");
            }
        } finally {
            server.stop(0);
        }

        assertThat(requestedPaths).containsExactly(
                "/maven2/jakarta/enterprise/jakarta.enterprise.lang-model/5.0.0/jakarta.enterprise.lang-model-5.0.0.pom",
                "/maven2/jakarta/enterprise/jakarta.enterprise.lang-model/5.0.1/jakarta.enterprise.lang-model-5.0.1.pom",
                "/maven2/jakarta/enterprise/jakarta.enterprise.lang-model/5.0.3/jakarta.enterprise.lang-model-5.0.3.pom");
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
