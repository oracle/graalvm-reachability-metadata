/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.graalvm.internal.tck.harness.TckExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SearchArtifactURLsTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void runPrintsPromptWithoutInvokingAgentForOpencodeRun() throws IOException, InterruptedException {
        Project project = createProjectWithMetadata();
        SearchArtifactURLsTask task = project.getTasks().create(
                "populateArtifactURLs",
                SearchArtifactURLsTask.class
        );
        task.setCoordinatesOption("ch.qos.logback:logback-classic:1.4.1");
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
                .contains("Find the repository URL, the sources URL, the test suite URL, and the documentation URL for the following library: ch.qos.logback:logback-classic:1.4.1")
                .contains("The sources URL, the test suite URL, and the documentation URL must be for the EXACT version \"1.4.1\".")
                .contains("Fill only missing URL fields among \"source-code-url\", \"repository-url\", \"test-code-url\", and \"documentation-url\".")
                .contains("Set missing \"repository-url\" to the selected repository URL.")
                .contains("\"repository-url\" must be the canonical repository root URL and must not include a version/tag/branch path (for example, no \"/tree/v_1.2.11\").")
                .contains("Set missing \"documentation-url\" to the selected project documentation URL for version \"1.4.1\".")
                .contains("If any of these URLs cannot be found with confidence, set that field value to \"N/A\".")
                .contains("Current URL values:")
                .contains("- source-code-url: <missing>")
                .contains("Entry selector: \"metadata-version\" = \"1.4.1\"")
                .doesNotContain("Source Artifact Verification (required):");
        assertThat(tempDir.resolve("build/agent-url-discovery-logs")).doesNotExist();
    }

    @Test
    void runWithLimitSkipsPrePopulatedCoordinatesAndStillPrompts() throws IOException, InterruptedException {
        Project project = createProjectWithMixedMetadata();
        SearchArtifactURLsTask task = project.getTasks().create(
                "populateArtifactURLs",
                SearchArtifactURLsTask.class
        );
        task.setAgentCommandOption("opencode run");
        task.setLimitOption("1");

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
                .contains("Find the repository URL, the sources URL, the test suite URL, and the documentation URL for the following library: b.group:bbb:2.0.0")
                .doesNotContain("Find the repository URL, the sources URL, the test suite URL, and the documentation URL for the following library: a.group:aaa:1.0.0");
    }

    @Test
    void runWithOverwriteExistingIncludesPrePopulatedCoordinates() throws IOException, InterruptedException {
        Project project = createProjectWithMixedMetadata();
        SearchArtifactURLsTask task = project.getTasks().create(
                "populateArtifactURLs",
                SearchArtifactURLsTask.class
        );
        task.setAgentCommandOption("opencode run");
        task.setOverwriteExistingOption(true);
        task.setLimitOption("1");

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
                .contains("Find the repository URL, the sources URL, the test suite URL, and the documentation URL for the following library: a.group:aaa:1.0.0")
                .contains("- Overwrite existing URL values.")
                .contains("- source-code-url: https://example.com/source-a");
    }

    @Test
    void runWithVerifyArtifactSourcesAddsVerificationPromptSection() throws IOException, InterruptedException {
        Project project = createProjectWithMetadata();
        SearchArtifactURLsTask task = project.getTasks().create(
                "populateArtifactURLs",
                SearchArtifactURLsTask.class
        );
        task.setCoordinatesOption("ch.qos.logback:logback-classic:1.4.1");
        task.setAgentCommandOption("opencode run");
        task.setVerifyArtifactSourcesOption(true);

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
                .contains("Source Artifact Verification (required):")
                .contains("Verify candidate source URLs for version \"1.4.1\", including Maven and non-Maven candidates.")
                .contains("confirm `-sources.jar` contains real source files")
                .contains("confirm `-test-sources.jar` contains real test source files.")
                .contains("For non-Maven source/test URLs")
                .contains("Prefer a verified repository tag URL instead.");
    }

    private Project createProjectWithMetadata() throws IOException {
        writeMetadataIndex(
                "ch.qos.logback",
                "logback-classic",
                "1.4.1",
                """
                [
                  {
                    "latest": true,
                    "allowed-packages": [
                      "ch.qos.logback"
                    ],
                    "metadata-version": "1.4.1",
                    "tested-versions": [
                      "1.4.1"
                    ]
                  }
                ]
                """
        );
        return createProjectSkeleton();
    }

    private Project createProjectWithMixedMetadata() throws IOException {
        writeMetadataIndex(
                "a.group",
                "aaa",
                "1.0.0",
                """
                [
                  {
                    "metadata-version": "1.0.0",
                    "tested-versions": [
                      "1.0.0"
                    ],
                    "source-code-url": "https://example.com/source-a",
                    "repository-url": "https://example.com/repository-a",
                    "test-code-url": "https://example.com/tests-a",
                    "documentation-url": "https://example.com/docs-a"
                  }
                ]
                """
        );
        writeMetadataIndex(
                "b.group",
                "bbb",
                "2.0.0",
                """
                [
                  {
                    "metadata-version": "2.0.0",
                    "tested-versions": [
                      "2.0.0"
                    ]
                  }
                ]
                """
        );
        return createProjectSkeleton();
    }

    private void writeMetadataIndex(String group, String artifact, String versionDir, String indexContent) throws IOException {
        Files.createDirectories(tempDir.resolve("metadata/" + group + "/" + artifact + "/" + versionDir));
        Files.writeString(
                tempDir.resolve("metadata/" + group + "/" + artifact + "/index.json"),
                indexContent
        );
    }

    private Project createProjectSkeleton() throws IOException {
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.getExtensions().create("tck", TckExtension.class, project);
        return project;
    }
}
