/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoverArtifactMetadataTests {

    @TempDir
    Path tempDir;

    @Test
    void runPrintsPromptForOpencodeRun() throws IOException, InterruptedException {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        DiscoverArtifactMetadata task = project.getTasks().create("discoverArtifactMetadata", DiscoverArtifactMetadata.class);
        task.setCoordinatesOption("io.ktor:ktor-server-core-jvm:3.1.0");
        task.setAgentCommandOption("opencode run");

        PrintStream originalOut = System.out;
        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        try (PrintStream capturedOut = new PrintStream(outputBuffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            task.run();
        } finally {
            System.setOut(originalOut);
        }

        String output = outputBuffer.toString(StandardCharsets.UTF_8);
        assertThat(output)
                .contains("Find the repository URL, the sources URL, the test suite URL, the documentation URL, and a concise two-sentence explanation for the following library: io.ktor:ktor-server-core-jvm:3.1.0")
                .contains("Also determine whether this is a language-specific library.")
                .contains("{ \"name\": \"scala\", \"version\": \"3\" }")
                .contains("If the library is not language-specific, leave the \"language\" field absent.")
                .contains("Update this file directly:")
                .contains("Set \"coordinates\" to \"io.ktor:ktor-server-core-jvm:3.1.0\".")
                .contains("build/discovered-artifact-metadata/io.ktor-ktor-server-core-jvm-3.1.0.json");
        assertThat(tempDir.resolve("build/agent-artifact-discovery-logs")).doesNotExist();
    }

    @Test
    void runDeletesInitializedDiscoveryFileWhenAgentCommandNotFound() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        DiscoverArtifactMetadata task = project.getTasks().create("discoverArtifactMetadata", DiscoverArtifactMetadata.class);
        task.setCoordinatesOption("io.ktor:ktor-server-core-jvm:3.1.0");
        task.setAgentCommandOption("definitely-not-a-real-agent-command-xyz");

        Path discoveryFile = tempDir.resolve("build/discovered-artifact-metadata/io.ktor-ktor-server-core-jvm-3.1.0.json");

        assertThatThrownBy(task::run).isInstanceOf(IOException.class);

        // A stale, initialized discovery file would silently block the next run, so it must be cleaned up.
        assertThat(Files.exists(discoveryFile)).isFalse();
    }
}
