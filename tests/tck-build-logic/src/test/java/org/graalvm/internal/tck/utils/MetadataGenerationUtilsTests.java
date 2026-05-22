/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.Coordinates;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataGenerationUtilsTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void makeVersionLatestInIndexJsonMovesCoveredTimestampVersionsToInsertedMetadataBoundary() throws IOException {
        String group = "com.graphql-java";
        String artifact = "java-dataloader";
        String newVersion = "2021-08-19T04-04-25-efb3c9d";
        writeIndex(
                group,
                artifact,
                """
                [
                  {
                    "latest" : true,
                    "metadata-version" : "2021-03-30T02-06-56-9a47743",
                    "tested-versions" : [
                      "2021-03-30T02-06-56-9a47743"
                    ],
                    "allowed-packages" : [
                      "org.dataloader"
                    ]
                  },
                  {
                    "metadata-version" : "2021-07-17T01-39-28-682c652",
                    "tested-versions" : [
                      "2021-07-17T01-39-28-682c652",
                      "2021-10-29T01-57-17-4253050"
                    ],
                    "allowed-packages" : [
                      "org.dataloader"
                    ]
                  }
                ]
                """
        );

        MetadataGenerationUtils.makeVersionLatestInIndexJson(
                createProject().getLayout(),
                Coordinates.parse(group + ":" + artifact + ":" + newVersion),
                null
        );

        List<Map<String, Object>> entries = readIndex(group, artifact);
        assertThat(entries.get(0))
                .containsEntry("latest", true)
                .containsEntry("metadata-version", newVersion)
                .containsEntry("tested-versions", List.of(
                        newVersion,
                        "2021-10-29T01-57-17-4253050"
                ));
        assertThat(findEntry(entries, "2021-07-17T01-39-28-682c652"))
                .containsEntry("tested-versions", List.of("2021-07-17T01-39-28-682c652"));
    }

    @Test
    void makeVersionLatestInIndexJsonDoesNotMoveVersionsCoveredByNextMetadataBoundary() throws IOException {
        String group = "com.example";
        String artifact = "demo";
        writeIndex(
                group,
                artifact,
                """
                [
                  {
                    "latest" : true,
                    "metadata-version" : "1.0.0",
                    "tested-versions" : [
                      "1.0.0",
                      "1.2.0"
                    ],
                    "allowed-packages" : [
                      "com.example"
                    ]
                  },
                  {
                    "metadata-version" : "2.0.0",
                    "tested-versions" : [
                      "2.0.0",
                      "2.1.0"
                    ],
                    "allowed-packages" : [
                      "com.example"
                    ]
                  }
                ]
                """
        );

        MetadataGenerationUtils.makeVersionLatestInIndexJson(
                createProject().getLayout(),
                Coordinates.parse(group + ":" + artifact + ":1.1.0"),
                null
        );

        List<Map<String, Object>> entries = readIndex(group, artifact);
        assertThat(entries.get(0))
                .containsEntry("metadata-version", "1.1.0")
                .containsEntry("tested-versions", List.of("1.1.0", "1.2.0"));
        assertThat(findEntry(entries, "1.0.0"))
                .containsEntry("tested-versions", List.of("1.0.0"));
        assertThat(findEntry(entries, "2.0.0"))
                .containsEntry("tested-versions", List.of("2.0.0", "2.1.0"));
    }

    @Test
    void addVersionToIndexJsonPreservesLatestEntryForHistoricalBackfill() throws IOException {
        String group = "com.graphql-java";
        String artifact = "graphql-java";
        String backfillVersion = "2018-06-27T00-18-28-1e12da9";
        writeIndex(
                group,
                artifact,
                """
                [
                  {
                    "latest" : true,
                    "metadata-version" : "19.7",
                    "tested-versions" : [
                      "19.7",
                      "24.3",
                      "25.0",
                      "26.0"
                    ],
                    "allowed-packages" : [
                      "graphql"
                    ]
                  },
                  {
                    "metadata-version" : "19.2",
                    "tested-versions" : [
                      "19.2",
                      "19.3"
                    ],
                    "allowed-packages" : [
                      "graphql"
                    ]
                  }
                ]
                """
        );

        MetadataGenerationUtils.addVersionToIndexJson(
                createProject().getLayout(),
                Coordinates.parse(group + ":" + artifact + ":" + backfillVersion),
                null
        );

        List<Map<String, Object>> entries = readIndex(group, artifact);
        assertThat(entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.get("latest")))
                .map(entry -> entry.get("metadata-version")))
                .containsExactly("19.7");
        assertThat(findEntry(entries, "19.7"))
                .containsEntry("tested-versions", List.of("19.7", "24.3", "25.0", "26.0"));
        assertThat(entries.get(1))
                .doesNotContainKey("latest")
                .containsEntry("metadata-version", backfillVersion)
                .containsEntry("tested-versions", List.of(backfillVersion))
                .containsEntry("allowed-packages", List.of("graphql"));
    }

    @Test
    void addVersionToIndexJsonUpdatingLatestWhenNewerPromotesFinalReleaseOverClassifierPrerelease() throws IOException {
        String group = "com.example";
        String artifact = "demo";
        writeIndex(
                group,
                artifact,
                """
                [
                  {
                    "latest" : true,
                    "metadata-version" : "3.0.0-M5-javax",
                    "tested-versions" : [
                      "3.0.0-M5-javax"
                    ],
                    "allowed-packages" : [
                      "com.example"
                    ]
                  }
                ]
                """
        );

        MetadataGenerationUtils.addVersionToIndexJsonUpdatingLatestWhenNewer(
                createProject().getLayout(),
                Coordinates.parse(group + ":" + artifact + ":3.0.0"),
                null
        );

        List<Map<String, Object>> entries = readIndex(group, artifact);
        assertThat(entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.get("latest")))
                .map(entry -> entry.get("metadata-version")))
                .containsExactly("3.0.0");
        assertThat(findEntry(entries, "3.0.0-M5-javax"))
                .doesNotContainKey("latest");
    }

    @Test
    void addVersionToIndexJsonUpdatingLatestWhenNewerPreservesLatestForClassifierPrerelease() throws IOException {
        String group = "com.example";
        String artifact = "demo";
        writeIndex(
                group,
                artifact,
                """
                [
                  {
                    "latest" : true,
                    "metadata-version" : "3.0.0",
                    "tested-versions" : [
                      "3.0.0"
                    ],
                    "allowed-packages" : [
                      "com.example"
                    ]
                  }
                ]
                """
        );

        MetadataGenerationUtils.addVersionToIndexJsonUpdatingLatestWhenNewer(
                createProject().getLayout(),
                Coordinates.parse(group + ":" + artifact + ":3.0.0-M5-javax"),
                null
        );

        List<Map<String, Object>> entries = readIndex(group, artifact);
        assertThat(entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.get("latest")))
                .map(entry -> entry.get("metadata-version")))
                .containsExactly("3.0.0");
        assertThat(findEntry(entries, "3.0.0-M5-javax"))
                .doesNotContainKey("latest")
                .containsEntry("tested-versions", List.of("3.0.0-M5-javax"));
    }

    @Test
    void discoverTestOnlyMetadataPackagesIgnoresDependencyApiStubs() throws IOException {
        Path testsDirectory = tempDir.resolve("tests/src/io.grpc/grpc-auth/1.79.0");
        writeSource(
                testsDirectory.resolve("src/test/java/com/google/auth/oauth2/ServiceAccountCredentials.java"),
                """
                package com.google.auth.oauth2;

                public final class ServiceAccountCredentials {
                }
                """
        );
        writeSource(
                testsDirectory.resolve("src/test/java/io_grpc/grpc_auth/GrpcAuthTest.java"),
                """
                package io_grpc.grpc_auth;

                import org.junit.jupiter.api.Test;

                public class GrpcAuthTest {
                    @Test
                    void exercisesAuth() {
                    }
                }
                """
        );

        assertThat(MetadataGenerationUtils.discoverTestPackages(testsDirectory))
                .containsExactlyInAnyOrder("com.google.auth.oauth2", "io_grpc.grpc_auth");
        assertThat(MetadataGenerationUtils.discoverTestOnlyMetadataPackages(testsDirectory))
                .containsExactly("io_grpc.grpc_auth");
    }

    private Project createProject() {
        return ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    private void writeSource(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.stripIndent(), StandardCharsets.UTF_8);
    }

    private void writeIndex(String group, String artifact, String content) throws IOException {
        Path indexFile = tempDir.resolve("metadata").resolve(group).resolve(artifact).resolve("index.json");
        Files.createDirectories(indexFile.getParent());
        Files.writeString(indexFile, content, StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> readIndex(String group, String artifact) throws IOException {
        return OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata").resolve(group).resolve(artifact).resolve("index.json").toFile(),
                new TypeReference<>() {
                }
        );
    }

    private Map<String, Object> findEntry(List<Map<String, Object>> entries, String metadataVersion) {
        return entries.stream()
                .filter(entry -> metadataVersion.equals(entry.get("metadata-version")))
                .findFirst()
                .orElseThrow();
    }
}
