/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import groovy.json.JsonSlurper;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangedRealUseMatrixTests {

    @TempDir
    Path projectDir;

    @Test
    void runsOnlyTheProjectsThePullRequestChanges() throws IOException, InterruptedException {
        writeFixtureProject();
        String forkPoint = commitAll("base");

        writeRealUseProject("demo");
        String pullRequestHead = commitAll("add demo real-use project");

        runCommand("git", "checkout", "-q", "--detach", forkPoint);
        writeRealUseProject("other");
        String baseBranchHead = commitAll("add other real-use project after the fork");

        // CI checks out the merge of the head into the base branch, which holds both projects.
        runCommand("git", "merge", "-q", "--no-ff", "--no-edit", pullRequestHead);

        assertThat(matrixCoordinates(baseBranchHead, pullRequestHead)).containsExactly("com.example:demo:1.0.0");
    }

    @Test
    void runsAProjectChangedOnTopOfTheBaseBranchHead() throws IOException, InterruptedException {
        writeFixtureProject();
        writeRealUseProject("other");
        String baseBranchHead = commitAll("base");

        writeRealUseProject("demo");
        String pullRequestHead = commitAll("add demo real-use project");

        assertThat(matrixCoordinates(baseBranchHead, pullRequestHead)).containsExactly("com.example:demo:1.0.0");
    }

    private void writeFixtureProject() throws IOException, InterruptedException {
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'real-use-matrix-fixture'\n");
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'base'
                    id 'org.graalvm.internal.tck-harness'
                }
                """);
        Files.writeString(projectDir.resolve("LICENSE"), "test");
        Files.createDirectories(projectDir.resolve("tests/tck-build-logic"));
        Files.writeString(projectDir.resolve("ci.json"), """
                {
                  "buildArgs": [],
                  "nativeImageModes": {
                    "current-defaults": []
                  },
                  "generateChangedRealUseMatrix": {
                    "java": ["latest-ea"],
                    "os": ["ubuntu-latest"],
                    "native-image-modes": ["current-defaults"]
                  }
                }
                """);
        for (String artifact : List.of("demo", "other")) {
            Files.createDirectories(projectDir.resolve("metadata/com.example/" + artifact + "/1.0.0"));
            Files.writeString(projectDir.resolve("metadata/com.example/" + artifact + "/index.json"), """
                    [
                      {
                        "metadata-version": "1.0.0",
                        "tested-versions": ["1.0.0"]
                      }
                    ]
                    """);
            Files.writeString(projectDir.resolve("metadata/com.example/" + artifact + "/1.0.0/reachability-metadata.json"), """
                    {
                      "resources": []
                    }
                    """);
        }
        runCommand("git", "init", "-q");
        runCommand("git", "config", "user.name", "TCK Test");
        runCommand("git", "config", "user.email", "tck-test@example.com");
    }

    /// Distinct contents, so git never pairs one project's files with another's as a rename.
    private void writeRealUseProject(String artifact) throws IOException {
        Path project = projectDir.resolve("real-use-tests/src/com.example/" + artifact + "/1.0.0");
        Files.createDirectories(project);
        Files.writeString(project.resolve("build.gradle"), "description = '" + artifact + " real-use project'\n");
    }

    private String commitAll(String message) throws IOException, InterruptedException {
        runCommand("git", "add", "-A");
        runCommand("git", "commit", "-q", "-m", message);
        return runCommand("git", "rev-parse", "HEAD").trim();
    }

    @SuppressWarnings("unchecked")
    private List<Object> matrixCoordinates(String baseCommit, String newCommit) {
        String output = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(
                        "generateChangedRealUseMatrix",
                        "-PbaseCommit=" + baseCommit,
                        "-PnewCommit=" + newCommit,
                        "--quiet"
                )
                .build()
                .getOutput();
        String matrixJson = output.lines()
                .filter(line -> line.startsWith("matrix="))
                .findFirst()
                .orElseThrow()
                .substring("matrix=".length());
        Map<String, Object> matrix = (Map<String, Object>) new JsonSlurper().parseText(matrixJson);
        return ((List<Map<String, Object>>) matrix.get("include")).stream()
                .map(entry -> entry.get("coordinates"))
                .distinct()
                .toList();
    }

    private String runCommand(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(projectDir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError(String.join(" ", command) + " failed with exit code " + exitCode + "\n" + output);
        }
        return output;
    }
}
