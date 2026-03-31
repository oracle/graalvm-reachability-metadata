/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataFilesCheckerTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void runUsesSharedMetadataVersionForSupportedVersion() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "reflection": [
                    {
                      "type": "com.example.Demo",
                      "allDeclaredMethods": true
                    }
                  ]
                }
                """
        );

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        TestMetadataFilesCheckerTask task = project.getTasks().create("checkMetadataFiles", TestMetadataFilesCheckerTask.class);
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatCode(task::run).doesNotThrowAnyException();
    }

    @Test
    void runFailsWhenReachabilityMetadataJsonIsMalformed() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                "{ not valid json"
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    @Test
    void runFailsWhenReachabilityMetadataViolatesSchema() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "reflection": [
                    {
                      "allDeclaredMethods": true
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    @Test
    void runFailsWhenReachabilityMetadataContainsDuplicatedReflectionEntries() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "reflection": [
                    {
                      "type": "com.example.Demo",
                      "allDeclaredMethods": true
                    },
                    {
                      "type": "com.example.Demo",
                      "allDeclaredMethods": true
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    @Test
    void runAllowsLegacySerializationEntriesWithRealSchema() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "serialization": [
                    {
                      "type": "java.lang.String"
                    },
                    {
                      "condition": {
                        "typeReached": "com.example.Demo"
                      },
                      "type": "com.example.Demo$State"
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatCode(task::run).doesNotThrowAnyException();
    }

    @Test
    void runFailsWhenLegacySerializationEntryViolatesSchema() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "serialization": [
                    {
                      "condition": {
                        "typeReached": "com.example.Demo"
                      }
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    private TestMetadataFilesCheckerTask createTask() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        return project.getTasks().create("checkMetadataFiles", TestMetadataFilesCheckerTask.class);
    }

    private void createMetadataIndex() throws IOException {
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/index.json"),
                """
                [
                  {
                    "latest": true,
                    "allowed-packages": [
                      "com.example"
                    ],
                    "metadata-version": "1.0.0",
                    "tested-versions": [
                      "1.0.0",
                      "1.0.1"
                    ]
                  }
                ]
                """
        );
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

    abstract static class TestMetadataFilesCheckerTask extends MetadataFilesCheckerTask {
        @Inject
        public TestMetadataFilesCheckerTask() {
        }
    }
}
