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

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixTestNativeImageRunTests {
    private static final String BASE_COORDINATES = "io.netty:netty-codec-smtp:4.1.74.Final";
    private static final String NEW_VERSION = "4.2.2.Final";
    private static final String NEW_COORDINATES = "io.netty:netty-codec-smtp:" + NEW_VERSION;

    @TempDir
    Path tempDir;

    @Test
    void runDoesNotUpdateIndexWhenMetadataGenerationFails() throws IOException {
        Project project = createProject();
        prepareLibraryProject();
        String originalIndex = Files.readString(indexFile(), StandardCharsets.UTF_8);
        createGradlewScript(true);
        TestFixTestNativeImageRun task = registerTask(project);

        assertThatThrownBy(task::run)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot generate metadata")
                .hasMessageContaining("agent failed");

        assertThat(Files.readString(indexFile(), StandardCharsets.UTF_8)).isEqualTo(originalIndex);
        assertThat(metadataFile(NEW_VERSION)).doesNotExist();
        assertThat(readGradlewInvocations())
                .contains("4.2.2.Final|-Pagent test")
                .doesNotContain("metadataCopy")
                .doesNotContain("test -Pcoordinates=" + NEW_COORDINATES);
    }

    @Test
    void runUpdatesIndexAfterMetadataGenerationAndTestsNewCoordinates() throws IOException {
        Project project = createProject();
        prepareLibraryProject();
        createGradlewScript(false);
        TestFixTestNativeImageRun task = registerTask(project);

        task.run();

        assertThat(metadataFile(NEW_VERSION)).exists();
        assertThat(Files.readString(indexFile(), StandardCharsets.UTF_8))
                .contains("\"metadata-version\" : \"4.2.2.Final\"")
                .contains("\"test-version\" : \"4.1.74.Final\"")
                .contains("\"tested-versions\" : [\n      \"4.2.2.Final\"\n    ]");
        assertThat(readGradlewInvocations())
                .contains("4.2.2.Final|-Pagent test")
                .contains("4.2.2.Final|metadataCopy --task test --dir " + metadataDirectory(NEW_VERSION))
                .contains("4.2.2.Final|test -Pcoordinates=" + NEW_COORDINATES);
    }

    private Project createProject() {
        return ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    private TestFixTestNativeImageRun registerTask(Project project) {
        TestFixTestNativeImageRun task = project.getTasks().register("fixTestNativeImageRun", TestFixTestNativeImageRun.class).get();
        task.setTestLibraryCoordinates(BASE_COORDINATES);
        task.setNewLibraryVersion(NEW_VERSION);
        return task;
    }

    private void prepareLibraryProject() throws IOException {
        Files.createDirectories(metadataDirectory("4.1.74.Final"));
        Files.writeString(metadataFile("4.1.74.Final"), "{\"reflection\":[]}\n", StandardCharsets.UTF_8);
        Files.writeString(
                indexFile(),
                """
                [
                  {
                    "latest" : true,
                    "metadata-version" : "4.1.74.Final",
                    "tested-versions" : [
                      "4.1.74.Final"
                    ],
                    "allowed-packages" : [
                      "io.netty.handler.codec.smtp"
                    ]
                  }
                ]
                """,
                StandardCharsets.UTF_8
        );

        Path testDirectory = tempDir.resolve("tests/src/io.netty/netty-codec-smtp/4.1.74.Final");
        Files.createDirectories(testDirectory);
        Files.writeString(
                testDirectory.resolve("build.gradle"),
                """
                plugins {
                    id "java"
                }

                graalvmNative {
                    agent {
                        defaultMode = "conditional"
                    }
                }
                """,
                StandardCharsets.UTF_8
        );
    }

    private void createGradlewScript(boolean failMetadataGeneration) throws IOException {
        Path logFile = tempDir.resolve("gradlew-invocations.log");
        Path gradlew = tempDir.resolve("gradlew");
        String failureBlock = failMetadataGeneration
                ? """
                  echo 'agent failed' >&2
                  exit 42
                """
                : "  exit 0\n";
        Files.writeString(
                gradlew,
                """
                #!/bin/sh
                printf '%%s|%%s|%%s\\n' "$PWD" "$GVM_TCK_LV" "$*" >> '%s'
                if [ "$1" = "-Pagent" ]; then
                %s
                fi
                if [ "$1" = "metadataCopy" ]; then
                  while [ "$#" -gt 0 ]; do
                    if [ "$1" = "--dir" ]; then
                      mkdir -p "$2"
                      printf '{"reflection":[]}\\n' > "$2/reachability-metadata.json"
                      break
                    fi
                    shift
                  done
                  exit 0
                fi
                if [ "$1" = "test" ]; then
                  exit 0
                fi
                exit 0
                """.formatted(logFile, failureBlock),
                StandardCharsets.UTF_8
        );
        boolean executable = gradlew.toFile().setExecutable(true);
        assertThat(executable).isTrue();
    }

    private String readGradlewInvocations() throws IOException {
        Path logFile = tempDir.resolve("gradlew-invocations.log");
        if (!Files.exists(logFile)) {
            return "";
        }
        return Files.readString(logFile, StandardCharsets.UTF_8);
    }

    private Path indexFile() {
        return tempDir.resolve("metadata/io.netty/netty-codec-smtp/index.json");
    }

    private Path metadataDirectory(String version) {
        return tempDir.resolve("metadata/io.netty/netty-codec-smtp").resolve(version);
    }

    private Path metadataFile(String version) {
        return metadataDirectory(version).resolve("reachability-metadata.json");
    }

    abstract static class TestFixTestNativeImageRun extends FixTestNativeImageRun {
        @Inject
        public TestFixTestNativeImageRun() {
        }
    }
}
