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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
