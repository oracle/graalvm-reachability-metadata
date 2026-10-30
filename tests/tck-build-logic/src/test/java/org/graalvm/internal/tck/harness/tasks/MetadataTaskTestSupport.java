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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/// Temporary repository fixtures shared by the `splitTestOnlyMetadata` and
/// `routeForeignMetadata` task tests.
abstract class MetadataTaskTestSupport {
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static final String LIB_SHIPPED_METADATA = "metadata/com.example/lib/1.0.0/reachability-metadata.json";
    static final String LIB_TEST_METADATA =
            "tests/src/com.example/lib/1.0.0/src/test/resources/META-INF/native-image/reachability-metadata.json";

    @TempDir
    Path tempDir;

    void writeMinimalTestProject(String group, String artifact, String version) throws IOException {
        writeSource(
                "tests/src/" + group + "/" + artifact + "/" + version + "/src/test/java/example/RootTest.java",
                """
                package example;

                final class RootTest {
                }
                """
        );
    }

    void writeIndex(
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
                    "auto-update": true,
                    "high-priority": true,
                    "metadata-version": "%s",
                    "tested-versions": %s,
                    "allowed-packages": %s
                  }
                ]
                """.formatted(metadataVersion, toJson(testedVersions), toJson(allowedPackages))
        );
    }

    void runSplitTask(String coordinate) {
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
    void writeLibTestProject() throws IOException {
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

    String foreignSerializationMetadata() {
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

    String toJson(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    void writeSource(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.stripIndent(), StandardCharsets.UTF_8);
    }

    void writeJson(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.stripIndent(), StandardCharsets.UTF_8);
    }

    JsonNode readJson(String relativePath) throws IOException {
        return OBJECT_MAPPER.readTree(tempDir.resolve(relativePath).toFile());
    }
}
