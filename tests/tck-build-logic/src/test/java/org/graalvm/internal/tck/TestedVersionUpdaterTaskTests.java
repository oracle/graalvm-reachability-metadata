/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;
import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestedVersionUpdaterTaskTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void runReplacesPreReleaseMetadataVersionAndStatsWhenFullReleaseIsAdded() throws IOException {
        String group = "com.example";
        String artifact = "demo";
        String oldVersion = "1.0.0-RC1";
        String newVersion = "1.0.0";

        writeIndex(group, artifact, oldVersion);
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0-RC1/reachability-metadata.json"),
                "{}\n",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                tempDir.resolve("tests/src/com.example/demo/1.0.0-RC1/gradle.properties"),
                """
                library.version = 1.0.0-RC1
                library.coordinates = com.example:demo:1.0.0-RC1
                metadata.dir = com.example/demo/1.0.0-RC1/
                """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                tempDir.resolve("stats/com.example/demo/1.0.0-RC1/stats.json"),
                """
                {
                  "versions" : [ {
                    "version" : "1.0.0-RC1",
                    "dynamicAccess" : "N/A",
                    "libraryCoverage" : {
                      "instruction" : "N/A",
                      "line" : "N/A",
                      "method" : "N/A"
                    }
                  } ]
                }
                """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                tempDir.resolve("stats/com.example/demo/1.0.0-RC1/execution-metrics.json"),
                """
                {
                  "add_new_library_support:2026-04-30": {
                    "library": "com.example:demo:1.0.0-RC1",
                    "previous_library": "com.example:demo:1.0.0-RC1",
                    "stats": {
                      "version": "1.0.0-RC1"
                    },
                    "previous_library_stats": {
                      "version": "1.0.0-RC1"
                    },
                    "artifacts": {
                      "test_file": "tests/src/com.example/demo/1.0.0-RC1/src/test/java/com/example/DemoTest.java",
                      "metadata_file": "metadata/com.example/demo/1.0.0-RC1/reachability-metadata.json"
                    }
                  }
                }
                """,
                StandardCharsets.UTF_8
        );
        Files.createDirectories(tempDir.resolve("stats/com.example/demo/2.0.0"));
        Files.writeString(
                tempDir.resolve("stats/com.example/demo/2.0.0/execution-metrics.json"),
                """
                {
                  "fix_javac_fail:2026-04-30": {
                    "library": "com.example:demo:2.0.0",
                    "previous_library": "com.example:demo:1.0.0-RC1",
                    "stats": {
                      "version": "2.0.0"
                    },
                    "previous_library_stats": {
                      "version": "1.0.0-RC1"
                    }
                  }
                }
                """,
                StandardCharsets.UTF_8
        );

        TestTestedVersionUpdaterTask task = createTask();
        task.setCoordinates(group + ":" + artifact + ":" + newVersion);
        task.getLastSupportedVersion().set(oldVersion);

        task.run();

        assertThat(tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json")).exists();
        assertThat(tempDir.resolve("metadata/com.example/demo/1.0.0-RC1")).doesNotExist();

        Path newTestDir = tempDir.resolve("tests/src/com.example/demo/1.0.0");
        assertThat(newTestDir).exists();
        assertThat(tempDir.resolve("tests/src/com.example/demo/1.0.0-RC1")).doesNotExist();
        assertThat(Files.readString(newTestDir.resolve("gradle.properties"), StandardCharsets.UTF_8))
                .contains("library.version = 1.0.0")
                .contains("library.coordinates = com.example:demo:1.0.0")
                .contains("metadata.dir = com.example/demo/1.0.0/");

        Path newStatsFile = tempDir.resolve("stats/com.example/demo/1.0.0/stats.json");
        assertThat(newStatsFile).exists();
        assertThat(tempDir.resolve("stats/com.example/demo/1.0.0-RC1/stats.json")).doesNotExist();
        assertThat(tempDir.resolve("stats/com.example/demo/1.0.0-RC1")).doesNotExist();
        assertThat(LibraryStatsSupport.loadMetadataVersionStats(newStatsFile).versions())
                .extracting(version -> version.version())
                .containsExactly("1.0.0");

        Path newMetricsFile = tempDir.resolve("stats/com.example/demo/1.0.0/execution-metrics.json");
        assertThat(newMetricsFile).exists();
        assertThat(tempDir.resolve("stats/com.example/demo/1.0.0-RC1/execution-metrics.json")).doesNotExist();
        JsonNode runMetrics = OBJECT_MAPPER.readTree(newMetricsFile.toFile()).get("add_new_library_support:2026-04-30");
        assertThat(runMetrics.get("library").asText()).isEqualTo("com.example:demo:1.0.0");
        assertThat(runMetrics.get("previous_library").asText()).isEqualTo("com.example:demo:1.0.0");
        assertThat(runMetrics.get("stats").get("version").asText()).isEqualTo("1.0.0");
        assertThat(runMetrics.get("previous_library_stats").get("version").asText()).isEqualTo("1.0.0");
        assertThat(runMetrics.get("artifacts").get("test_file").asText())
                .isEqualTo("tests/src/com.example/demo/1.0.0/src/test/java/com/example/DemoTest.java");
        assertThat(runMetrics.get("artifacts").get("metadata_file").asText())
                .isEqualTo("metadata/com.example/demo/1.0.0/reachability-metadata.json");

        Path newerMetricsFile = tempDir.resolve("stats/com.example/demo/2.0.0/execution-metrics.json");
        JsonNode newerRunMetrics = OBJECT_MAPPER.readTree(newerMetricsFile.toFile()).get("fix_javac_fail:2026-04-30");
        assertThat(newerRunMetrics.get("library").asText()).isEqualTo("com.example:demo:2.0.0");
        assertThat(newerRunMetrics.get("previous_library").asText()).isEqualTo("com.example:demo:1.0.0");
        assertThat(newerRunMetrics.get("stats").get("version").asText()).isEqualTo("2.0.0");
        assertThat(newerRunMetrics.get("previous_library_stats").get("version").asText()).isEqualTo("1.0.0");

        List<Map<String, Object>> indexEntries = OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata/com.example/demo/index.json").toFile(),
                new TypeReference<>() {
                }
        );
        assertThat(indexEntries).hasSize(2);
        assertThat(indexEntries.get(0)).containsEntry("metadata-version", "1.0.0")
                .containsEntry("tested-versions", List.of("1.0.0"));
        assertThat(indexEntries.get(1)).containsEntry("test-version", "1.0.0");
    }

    @Test
    void runPromotesMavenSourceAndJavadocUrlsWhenRenderedCandidatesVerify() throws IOException {
        String group = "com.example";
        String artifact = "demo";
        String oldVersion = "1.0.0-M20";
        String newVersion = "1.0.0";
        byte[] sourceJar = zipArchive("com/example/Demo.java", "package com.example; class Demo {}");
        HttpServer server = startServer(Map.of(
                "/maven/com/example/demo/1.0.0/demo-1.0.0-sources.jar", sourceJar,
                "/maven/com/example/demo/1.0.0/demo-1.0.0-javadoc.jar", zipArchive("index.html", "<html></html>")
        ));

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            writeIndex(
                    group,
                    artifact,
                    oldVersion,
                    baseUrl + "/maven/com/example/demo/1.0.0-M20/demo-1.0.0-M20-sources.jar",
                    "N/A",
                    baseUrl + "/maven/com/example/demo/1.0.0-M20/demo-1.0.0-M20-javadoc.jar"
            );

            TestTestedVersionUpdaterTask task = createTask();
            task.setCoordinates(group + ":" + artifact + ":" + newVersion);
            task.getLastSupportedVersion().set(oldVersion);

            task.run();

            List<Map<String, Object>> indexEntries = readIndex(group, artifact);
            assertThat(indexEntries.get(0))
                    .containsEntry("metadata-version", newVersion)
                    .containsEntry("source-code-url", baseUrl + "/maven/com/example/demo/1.0.0/demo-1.0.0-sources.jar")
                    .containsEntry("documentation-url", baseUrl + "/maven/com/example/demo/1.0.0/demo-1.0.0-javadoc.jar");
            assertThat(indexEntries.get(0)).containsEntry("test-code-url", "N/A");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runPromotesRepositoryTestUrlWhenRenderedCandidateVerifies() throws IOException {
        String group = "com.example";
        String artifact = "demo";
        String oldVersion = "2.0.0-RC1";
        String newVersion = "2.0.0";
        HttpServer server = startServer(Map.of(
                "/svn/demo/tags/2.0.0/src/test", "tests".getBytes(StandardCharsets.UTF_8)
        ));

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            writeIndex(
                    group,
                    artifact,
                    oldVersion,
                    "N/A",
                    baseUrl + "/svn/demo/tags/2.0.0-RC1/src/test",
                    "N/A"
            );

            TestTestedVersionUpdaterTask task = createTask();
            task.setCoordinates(group + ":" + artifact + ":" + newVersion);
            task.getLastSupportedVersion().set(oldVersion);

            task.run();

            assertThat(readIndex(group, artifact).get(0))
                    .containsEntry("metadata-version", newVersion)
                    .containsEntry("test-code-url", baseUrl + "/svn/demo/tags/2.0.0/src/test");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runFailsWithoutRenamingWhenPromotedUrlCannotVerify() throws IOException {
        String group = "com.example";
        String artifact = "demo";
        String oldVersion = "3.0.0-RC1";
        String newVersion = "3.0.0";
        HttpServer server = startServer(Map.of());

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            writeIndex(
                    group,
                    artifact,
                    oldVersion,
                    baseUrl + "/maven/com/example/demo/3.0.0-RC1/demo-3.0.0-RC1-sources.jar",
                    "N/A",
                    "N/A"
            );

            TestTestedVersionUpdaterTask task = createTask();
            task.setCoordinates(group + ":" + artifact + ":" + newVersion);
            task.getLastSupportedVersion().set(oldVersion);

            assertThatThrownBy(task::run)
                    .isInstanceOf(GradleException.class)
                    .hasMessageContaining("Cannot promote source-code-url")
                    .hasMessageContaining(baseUrl + "/maven/com/example/demo/3.0.0/demo-3.0.0-sources.jar")
                    .hasMessageContaining("HTTP 404");
            assertThat(tempDir.resolve("metadata/com.example/demo/3.0.0-RC1")).exists();
            assertThat(tempDir.resolve("metadata/com.example/demo/3.0.0")).doesNotExist();
        } finally {
            server.stop(0);
        }
    }

    private TestTestedVersionUpdaterTask createTask() {
        return ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
                .getTasks()
                .create("addTestedVersion", TestTestedVersionUpdaterTask.class);
    }

    private void writeIndex(String group, String artifact, String oldVersion) throws IOException {
        writeIndex(group, artifact, oldVersion, null, null, null);
    }

    private void writeIndex(String group, String artifact, String oldVersion, String sourceCodeUrl, String testCodeUrl, String documentationUrl) throws IOException {
        Path indexFile = tempDir.resolve("metadata/" + group + "/" + artifact + "/index.json");
        Files.createDirectories(indexFile.getParent());
        Files.writeString(
                indexFile,
                """
                [
                  {
                    "latest": true,
                    "allowed-packages": [
                      "com.example"
                    ],
                    "metadata-version": "%s",
                %s
                    "tested-versions": [
                      "%s"
                    ]
                  },
                  {
                    "allowed-packages": [
                      "com.example"
                    ],
                    "metadata-version": "0.9.0",
                    "test-version": "%s",
                    "tested-versions": [
                      "0.9.0"
                    ]
                  }
                ]
                """.formatted(oldVersion, urlFields(sourceCodeUrl, testCodeUrl, documentationUrl), oldVersion, oldVersion),
                StandardCharsets.UTF_8
        );
        Files.createDirectories(tempDir.resolve("metadata/" + group + "/" + artifact + "/" + oldVersion));
        Files.createDirectories(tempDir.resolve("tests/src/" + group + "/" + artifact + "/" + oldVersion));
        Files.createDirectories(tempDir.resolve("stats/" + group + "/" + artifact + "/" + oldVersion));
    }

    private String urlFields(String sourceCodeUrl, String testCodeUrl, String documentationUrl) {
        StringBuilder fields = new StringBuilder();
        appendUrlField(fields, "source-code-url", sourceCodeUrl);
        appendUrlField(fields, "test-code-url", testCodeUrl);
        appendUrlField(fields, "documentation-url", documentationUrl);
        return fields.toString();
    }

    private void appendUrlField(StringBuilder fields, String fieldName, String value) {
        if (value != null) {
            fields.append("    \"").append(fieldName).append("\": \"").append(value).append("\",\n");
        }
    }

    private List<Map<String, Object>> readIndex(String group, String artifact) throws IOException {
        return OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata/" + group + "/" + artifact + "/index.json").toFile(),
                new TypeReference<>() {
                }
        );
    }

    private HttpServer startServer(Map<String, byte[]> files) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = files.get(exchange.getRequestURI().getPath());
            int statusCode = body == null ? 404 : 200;
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(statusCode, -1);
            } else {
                byte[] responseBody = body == null ? new byte[0] : body;
                exchange.sendResponseHeaders(statusCode, responseBody.length);
                try (OutputStream responseStream = exchange.getResponseBody()) {
                    responseStream.write(responseBody);
                }
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private byte[] zipArchive(String entryName, String content) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }

    abstract static class TestTestedVersionUpdaterTask extends TestedVersionUpdaterTask {
        @Inject
        public TestTestedVersionUpdaterTask() {
        }
    }
}
