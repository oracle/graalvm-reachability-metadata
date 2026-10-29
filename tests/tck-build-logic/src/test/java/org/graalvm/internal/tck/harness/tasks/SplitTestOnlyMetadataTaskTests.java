/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SplitTestOnlyMetadataTaskTests extends MetadataTaskTestSupport {
    private static final String LIB_SHIPPED_METADATA = "metadata/com.example/lib/1.0.0/reachability-metadata.json";
    private static final String LIB_TEST_METADATA =
            "tests/src/com.example/lib/1.0.0/src/test/resources/META-INF/native-image/reachability-metadata.json";

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
    void movesLambdaDeclaredInTestClass() throws IOException {
        JsonNode lambdaEntry = splitLambdaMetadata("com_example.lib.LibTest", List.of("com.example.Loader"));

        assertThat(readJson(LIB_SHIPPED_METADATA).has("reflection")).isFalse();
        assertThat(readJson(LIB_TEST_METADATA).get("reflection")).containsExactly(lambdaEntry);
    }

    @Test
    void keepsLibraryLambdaWithLibraryInterfaces() throws IOException {
        JsonNode lambdaEntry = splitLambdaMetadata("com.example.Library", List.of("com.example.Loader"));

        assertThat(readJson(LIB_SHIPPED_METADATA).get("reflection")).containsExactly(lambdaEntry);
        assertThat(tempDir.resolve(LIB_TEST_METADATA)).doesNotExist();
    }

    @Test
    void movesLambdaImplementingTestInterface() throws IOException {
        JsonNode lambdaEntry = splitLambdaMetadata(
                "com.example.Library",
                List.of("com.example.Loader", "com_example.lib.LibTest$Callback")
        );

        assertThat(readJson(LIB_SHIPPED_METADATA).has("reflection")).isFalse();
        assertThat(readJson(LIB_TEST_METADATA).get("reflection")).containsExactly(lambdaEntry);
    }

    @Test
    void movesTestClassFileResource() throws IOException {
        assertResourceMoves("com_example/lib/LibTest$Report.class");
    }

    @Test
    void movesTestClassFileResourceWithLeadingSlash() throws IOException {
        assertResourceMoves("/com_example/lib/LibTest.class");
    }

    @Test
    void movesTestSubpackageClassFileResource() throws IOException {
        assertResourceMoves("com_example/lib/sub/Fixture.class");
    }

    @Test
    void movesWildcardClassFileResourceInTestPackage() throws IOException {
        assertResourceMoves("com_example/lib/*.class");
    }

    @Test
    void movesRecursiveWildcardClassFileResourceInTestPackage() throws IOException {
        assertResourceMoves("com_example/lib/**/*.class");
    }

    @Test
    void keepsLibraryClassFileResource() throws IOException {
        assertResourceStaysShipped("com/example/Library.class");
    }

    @Test
    void keepsClassFileResourceInPackageSharingTestPackagePrefix() throws IOException {
        assertResourceStaysShipped("com_example/libx/Helper.class");
    }

    @Test
    void keepsClassFileResourceNamedLikeTestPackage() throws IOException {
        assertResourceStaysShipped("com_example/lib.class");
    }

    @Test
    void keepsWildcardClassFileResourceAboveTestPackage() throws IOException {
        assertResourceStaysShipped("com_example/*.class");
    }

    @Test
    void keepsClassFileResourceOfTestPlacedInAllowedPackage() throws IOException {
        writeSource(
                "tests/src/com.example/lib/1.0.0/src/test/java/com/example/internal/InternalTest.java",
                """
                package com.example.internal;

                import org.junit.jupiter.api.Test;

                class InternalTest {
                    @Test
                    void readsInternals() {
                    }
                }
                """
        );

        assertResourceStaysShipped("com/example/internal/Helper.class");
    }

    @Test
    void keepsClassFileResourceOfDependencyStub() throws IOException {
        writeSource(
                "tests/src/com.example/lib/1.0.0/src/test/java/com/acme/codec/Codec.java",
                """
                package com.acme.codec;

                public class Codec {
                }
                """
        );

        assertResourceStaysShipped("com/acme/codec/Codec.class");
    }

    @Test
    void retainsExistingTestClassFileResource() throws IOException {
        writeLibTestProject();
        writeJson(LIB_SHIPPED_METADATA, "{}");
        String resourceEntry = classFileResourceEntry("com_example/lib/LibTest$Report.class");
        writeJson(LIB_TEST_METADATA, """
                {
                  "resources": [%s]
                }
                """.formatted(resourceEntry));

        runSplitTask("com.example:lib:1.0.0");

        assertThat(readJson(LIB_SHIPPED_METADATA).isEmpty()).isTrue();
        assertThat(readJson(LIB_TEST_METADATA).get("resources"))
                .containsExactly(OBJECT_MAPPER.readTree(resourceEntry));
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

    /// Writes the `com.example:lib:1.0.0` test project, whose test package is `com_example.lib`.
    private void writeLibTestProject() throws IOException {
        writeSource(
                "tests/src/com.example/lib/1.0.0/src/test/java/com_example/lib/LibTest.java",
                """
                package com_example.lib;

                import org.junit.jupiter.api.Test;

                public class LibTest {
                    @Test
                    void exercisesLibrary() {
                    }

                    interface Callback {
                    }
                }
                """
        );
        writeIndex("com.example", "lib", "1.0.0", List.of("1.0.0"), List.of("com.example"));
    }

    /// Writes a `com.example:lib:1.0.0` project whose only metadata entry is a lambda gated on a
    /// library type, splits it, and returns that entry.
    private JsonNode splitLambdaMetadata(String declaringClass, List<String> interfaces) throws IOException {
        writeLibTestProject();
        String lambdaEntry = """
                {
                  "condition": {
                    "typeReached": "com.example.Library"
                  },
                  "type": {
                    "lambda": {
                      "declaringClass": "%s",
                      "interfaces": %s
                    }
                  }
                }
                """.formatted(declaringClass, toJson(interfaces));
        writeJson(
                LIB_SHIPPED_METADATA,
                """
                {
                  "reflection": [%s]
                }
                """.formatted(lambdaEntry)
        );

        runSplitTask("com.example:lib:1.0.0");
        return OBJECT_MAPPER.readTree(lambdaEntry);
    }

    /// Writes a `com.example:lib:1.0.0` project whose only metadata entry is a resource `glob`
    /// gated on a library type, splits it, and returns that entry.
    private JsonNode splitResourceMetadata(String glob) throws IOException {
        writeLibTestProject();
        String resourceEntry = classFileResourceEntry(glob);
        writeJson(
                LIB_SHIPPED_METADATA,
                """
                {
                  "resources": [%s]
                }
                """.formatted(resourceEntry)
        );

        runSplitTask("com.example:lib:1.0.0");
        return OBJECT_MAPPER.readTree(resourceEntry);
    }

    private void assertResourceMoves(String glob) throws IOException {
        JsonNode resourceEntry = splitResourceMetadata(glob);

        assertThat(readJson(LIB_SHIPPED_METADATA).has("resources")).isFalse();
        assertThat(readJson(LIB_TEST_METADATA).get("resources")).containsExactly(resourceEntry);
    }

    private void assertResourceStaysShipped(String glob) throws IOException {
        JsonNode resourceEntry = splitResourceMetadata(glob);

        assertThat(readJson(LIB_SHIPPED_METADATA).get("resources")).containsExactly(resourceEntry);
        assertThat(tempDir.resolve(LIB_TEST_METADATA)).doesNotExist();
    }

    /// A resource entry as the agent records it when a library reads class bytes as a resource.
    private String classFileResourceEntry(String glob) {
        return """
                {
                  "condition": {
                    "typeReached": "com.example.LineNumbers"
                  },
                  "glob": "%s"
                }
                """.formatted(glob);
    }
}
