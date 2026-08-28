/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitTestOnlyMetadataTaskTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void leavesDependencyPackageStubMetadataShippedAndMovesTestFixtureMetadata() throws IOException {
        String coordinate = "io.grpc:grpc-auth:1.79.0";
        writeSource(
                "tests/src/io.grpc/grpc-auth/1.79.0/src/test/java/com/google/auth/oauth2/ServiceAccountCredentials.java",
                """
                package com.google.auth.oauth2;

                public final class ServiceAccountCredentials {
                    public String getClientId() {
                        return "client";
                    }
                }
                """
        );
        writeSource(
                "tests/src/io.grpc/grpc-auth/1.79.0/src/test/java/io_grpc/grpc_auth/GrpcAuthTest.java",
                """
                package io_grpc.grpc_auth;

                import org.junit.jupiter.api.Test;

                public class GrpcAuthTest {
                    @Test
                    void exercisesAuth() {
                    }

                    static final class Fixture {
                    }
                }
                """
        );
        writeJson(
                "metadata/io.grpc/grpc-auth/index.json",
                """
                [
                  {
                    "latest": true,
                    "metadata-version": "1.79.0",
                    "tested-versions": ["1.79.0"],
                    "allowed-packages": ["io.grpc"]
                  }
                ]
                """
        );
        writeJson(
                "metadata/io.grpc/grpc-auth/1.79.0/reachability-metadata.json",
                """
                {
                  "reflection": [
                    {
                      "condition": {
                        "typeReached": "io.grpc.auth.GoogleAuthLibraryCallCredentials$JwtHelper"
                      },
                      "type": "com.google.auth.oauth2.ServiceAccountCredentials",
                      "methods": [
                        {
                          "name": "getClientId",
                          "parameterTypes": []
                        }
                      ]
                    },
                    {
                      "condition": {
                        "typeReached": "io_grpc.grpc_auth.GrpcAuthTest"
                      },
                      "type": "io_grpc.grpc_auth.GrpcAuthTest$Fixture"
                    }
                  ]
                }
                """
        );

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        SplitTestOnlyMetadataTask task = project.getTasks()
                .register("splitTestOnlyMetadata", SplitTestOnlyMetadataTask.class)
                .get();
        task.setCoordinatesOverride(List.of(coordinate));

        task.run();

        JsonNode shippedMetadata = readJson("metadata/io.grpc/grpc-auth/1.79.0/reachability-metadata.json");
        JsonNode testOnlyMetadata = readJson(
                "tests/src/io.grpc/grpc-auth/1.79.0/src/test/resources/META-INF/native-image/reachability-metadata.json"
        );

        assertThat(shippedMetadata.get("reflection"))
                .extracting(entry -> entry.get("type").asText())
                .containsExactly("com.google.auth.oauth2.ServiceAccountCredentials");
        assertThat(testOnlyMetadata.get("reflection"))
                .extracting(entry -> entry.get("type").asText())
                .containsExactly("io_grpc.grpc_auth.GrpcAuthTest$Fixture");
    }

    @Test
    void splitLeavesForeignConditionMetadataUntouched() throws IOException {
        String coordinate = "com.example:root:2.0.0";
        writeMinimalTestProject("com.example", "root", "2.0.0");
        writeIndex("com.example", "root", "2.0.0", List.of("2.0.0"), List.of("com.example"));
        writeIndex("org.owner", "engine", "2.0.0", List.of("2.0.0"), List.of("org.owner"));
        writeJson(
                "metadata/com.example/root/2.0.0/reachability-metadata.json",
                foreignSerializationMetadata()
        );
        writeJson("metadata/org.owner/engine/2.0.0/reachability-metadata.json", "{}");

        runSplitTask(coordinate);

        JsonNode sourceMetadata = readJson("metadata/com.example/root/2.0.0/reachability-metadata.json");
        JsonNode ownerMetadata = readJson("metadata/org.owner/engine/2.0.0/reachability-metadata.json");
        assertThat(sourceMetadata.get("serialization"))
                .extracting(entry -> entry.get("type").asText())
                .containsExactly("org.owner.Engine$SerializedForm");
        assertThat(ownerMetadata.isEmpty()).isTrue();
    }

    @Test
    void relocatesForeignConditionToExactSupportedOwner() throws IOException {
        String coordinate = "com.example:root:2.0.0";
        copyReachabilitySchemaFile();
        writeMinimalTestProject("com.example", "root", "2.0.0");
        writeIndex("com.example", "root", "2.0.0", List.of("2.0.0"), List.of("com.example"));
        writeIndex("org.owner", "engine", "2.0.0", List.of("2.0.0"), List.of("org.owner"));
        writeJson(
                "metadata/com.example/root/2.0.0/reachability-metadata.json",
                foreignSerializationMetadata()
        );
        writeJson(
                "metadata/org.owner/engine/2.0.0/reachability-metadata.json",
                """
                {
                  "reflection": [
                    {
                      "condition": {
                        "typeReached": "org.owner.Engine"
                      },
                      "type": "org.owner.Engine"
                    }
                  ]
                }
                """
        );

        runRouteTask(coordinate);

        JsonNode sourceMetadata = readJson("metadata/com.example/root/2.0.0/reachability-metadata.json");
        JsonNode ownerMetadata = readJson("metadata/org.owner/engine/2.0.0/reachability-metadata.json");
        assertThat(sourceMetadata.isEmpty()).isTrue();
        assertThat(ownerMetadata.get("serialization"))
                .extracting(entry -> entry.get("type").asText())
                .containsExactly("org.owner.Engine$SerializedForm");
    }

    @Test
    void forksSharedOwnerBucketBeforeRelocatingEntry() throws IOException {
        String coordinate = "com.example:root:2.0.0";
        copyReachabilitySchemaFile();
        writeMinimalTestProject("com.example", "root", "2.0.0");
        writeIndex("com.example", "root", "2.0.0", List.of("2.0.0"), List.of("com.example"));
        writeIndex("org.owner", "engine", "1.0.0", List.of("1.0.0", "2.0.0"), List.of("org.owner"));
        writeJson(
                "metadata/com.example/root/2.0.0/reachability-metadata.json",
                foreignSerializationMetadata()
        );
        writeJson(
                "metadata/org.owner/engine/1.0.0/reachability-metadata.json",
                """
                {
                  "reflection": [
                    {
                      "condition": {
                        "typeReached": "org.owner.Engine"
                      },
                      "type": "org.owner.Engine"
                    }
                  ]
                }
                """
        );

        RecordingRouteForeignMetadataTask task = runRouteTask(coordinate);

        JsonNode inheritedMetadata = readJson("metadata/org.owner/engine/1.0.0/reachability-metadata.json");
        JsonNode exactMetadata = readJson("metadata/org.owner/engine/2.0.0/reachability-metadata.json");
        JsonNode ownerIndex = readJson("metadata/org.owner/engine/index.json");
        assertThat(inheritedMetadata.has("serialization")).isFalse();
        assertThat(exactMetadata.get("reflection")).isEqualTo(inheritedMetadata.get("reflection"));
        assertThat(exactMetadata.get("serialization"))
                .extracting(entry -> entry.get("type").asText())
                .containsExactly("org.owner.Engine$SerializedForm");
        assertThat(ownerIndex.get(0).get("tested-versions"))
                .extracting(JsonNode::asText)
                .containsExactly("1.0.0");
        assertThat(ownerIndex.get(1).get("metadata-version").asText()).isEqualTo("2.0.0");
        assertThat(ownerIndex.get(1).get("test-version").asText()).isEqualTo("1.0.0");
        assertThat(ownerIndex.get(1).get("tested-versions"))
                .extracting(JsonNode::asText)
                .containsExactly("2.0.0");
        assertThat(task.generatedStatsCoordinates())
                .containsExactly("org.owner:engine:2.0.0");
    }

    @Test
    void leavesUnresolvedForeignEntryUntouched() throws IOException {
        String coordinate = "com.example:root:2.0.0";
        writeMinimalTestProject("com.example", "root", "2.0.0");
        writeIndex("com.example", "root", "2.0.0", List.of("2.0.0"), List.of("com.example"));
        writeJson(
                "metadata/com.example/root/2.0.0/reachability-metadata.json",
                foreignSerializationMetadata()
        );

        assertThatThrownBy(() -> runRouteTask(coordinate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no supported artifact maps org.owner.Engine");

        JsonNode sourceMetadata = readJson("metadata/com.example/root/2.0.0/reachability-metadata.json");
        assertThat(sourceMetadata.get("serialization"))
                .extracting(entry -> entry.get("type").asText())
                .containsExactly("org.owner.Engine$SerializedForm");
    }

    private void runSplitTask(String coordinate) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        SplitTestOnlyMetadataTask task = project.getTasks()
                .register("splitTestOnlyMetadata", SplitTestOnlyMetadataTask.class)
                .get();
        task.setCoordinatesOverride(List.of(coordinate));
        task.run();
    }

    private RecordingRouteForeignMetadataTask runRouteTask(String coordinate) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        RecordingRouteForeignMetadataTask task = project.getTasks()
                .register("routeForeignMetadata", RecordingRouteForeignMetadataTask.class)
                .get();
        task.setCoordinatesOverride(List.of(coordinate));
        task.run();
        return task;
    }

    public abstract static class RecordingRouteForeignMetadataTask extends RouteForeignMetadataTask {
        private final Set<String> generatedStatsCoordinates = new LinkedHashSet<>();

        @Override
        protected void generateRelocatedOwnerStats(Set<String> relocatedOwners) {
            generatedStatsCoordinates.addAll(relocatedOwners);
        }

        Set<String> generatedStatsCoordinates() {
            return generatedStatsCoordinates;
        }
    }

    private void writeMinimalTestProject(String group, String artifact, String version) throws IOException {
        writeSource(
                "tests/src/" + group + "/" + artifact + "/" + version + "/src/test/java/example/RootTest.java",
                """
                package example;

                final class RootTest {
                }
                """
        );
    }

    private void writeIndex(
            String group,
            String artifact,
            String metadataVersion,
            List<String> testedVersions,
            List<String> allowedPackages
    ) throws IOException {
        writeJson(
                "metadata/" + group + "/" + artifact + "/index.json",
                """
                [
                  {
                    "latest": true,
                    "metadata-version": "%s",
                    "tested-versions": %s,
                    "allowed-packages": %s
                  }
                ]
                """.formatted(metadataVersion, toJson(testedVersions), toJson(allowedPackages))
        );
    }

    private String foreignSerializationMetadata() {
        return """
                {
                  "serialization": [
                    {
                      "condition": {
                        "typeReached": "org.owner.Engine"
                      },
                      "type": "org.owner.Engine$SerializedForm"
                    }
                  ]
                }
                """;
    }

    private String toJson(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private void copyReachabilitySchemaFile() throws IOException {
        Path source = findRepoFile("metadata/schemas/reachability-metadata-schema-v1.2.0.json");
        Path target = tempDir.resolve("metadata/schemas/reachability-metadata-schema-v1.2.0.json");
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }

    private static Path findRepoFile(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relativePath + " from " + Path.of("").toAbsolutePath());
    }

    private void writeSource(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.stripIndent(), StandardCharsets.UTF_8);
    }

    private void writeJson(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.stripIndent(), StandardCharsets.UTF_8);
    }

    private JsonNode readJson(String relativePath) throws IOException {
        return OBJECT_MAPPER.readTree(tempDir.resolve(relativePath).toFile());
    }
}
