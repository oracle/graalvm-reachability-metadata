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

class ChangedMetadataTestMatrixTests {

    @TempDir
    Path projectDir;

    @Test
    void runsFullBatchesWhenOnlyMetadataChanges() throws IOException, InterruptedException {
        writeFixtureProject("""
                [
                  {
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0", "1.0.1", "1.0.2"]
                  }
                ]
                """);
        String baseCommit = commitAll("base");

        writeMetadataFile("1.0.0", """
                {
                  "resources": [
                    {
                      "glob": "changed.properties"
                    }
                  ]
                }
                """);
        commitAll("change metadata");

        assertThat(matrixEntries(baseCommit)).containsExactly(
                matrixEntry("com.example:demo:1.0.0", List.of("1.0.0", "1.0.1"), "1/2"),
                matrixEntry("com.example:demo:1.0.0", List.of("1.0.2"), "2/2")
        );
    }

    @Test
    void runsOnlyAddedBatchWhenOnlyTestedVersionsChange() throws IOException, InterruptedException {
        writeFixtureProject("""
                [
                  {
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0"]
                  }
                ]
                """);
        String baseCommit = commitAll("base");

        writeIndex("""
                [
                  {
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0", "1.0.1"]
                  }
                ]
                """);
        commitAll("add tested version");

        assertThat(matrixEntries(baseCommit)).containsExactly(
                matrixEntry("com.example:demo:1.0.0", List.of("1.0.1"), "added")
        );
    }

    @Test
    void skipsAddedBatchWhenSameMetadataCoordinateChanged() throws IOException, InterruptedException {
        writeFixtureProject("""
                [
                  {
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0"]
                  }
                ]
                """);
        String baseCommit = commitAll("base");

        writeIndex("""
                [
                  {
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0", "1.0.1"]
                  }
                ]
                """);
        writeMetadataFile("1.0.0", """
                {
                  "resources": [
                    {
                      "glob": "changed.properties"
                    }
                  ]
                }
                """);
        commitAll("change metadata and add tested version");

        assertThat(matrixEntries(baseCommit)).containsExactly(
                matrixEntry("com.example:demo:1.0.0", List.of("1.0.0", "1.0.1"), "1/1")
        );
    }

    @Test
    void keepsAddedBatchForDifferentMetadataCoordinate() throws IOException, InterruptedException {
        writeFixtureProject("""
                [
                  {
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0"]
                  },
                  {
                    "metadata-version": "2.0.0",
                    "tested-versions": ["2.0.0", "2.0.1", "2.0.2"]
                  }
                ]
                """);
        writeMetadataFile("2.0.0", """
                {
                  "resources": []
                }
                """);
        String baseCommit = commitAll("base");

        writeIndex("""
                [
                  {
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0", "1.0.1"]
                  },
                  {
                    "metadata-version": "2.0.0",
                    "tested-versions": ["2.0.0", "2.0.1", "2.0.2"]
                  }
                ]
                """);
        writeMetadataFile("2.0.0", """
                {
                  "resources": [
                    {
                      "glob": "changed.properties"
                    }
                  ]
                }
                """);
        commitAll("change different metadata and add tested version");

        assertThat(matrixEntries(baseCommit)).containsExactly(
                matrixEntry("com.example:demo:1.0.0", List.of("1.0.1"), "added"),
                matrixEntry("com.example:demo:2.0.0", List.of("2.0.0", "2.0.1"), "1/2"),
                matrixEntry("com.example:demo:2.0.0", List.of("2.0.2"), "2/2")
        );
    }

    private void writeFixtureProject(String metadataIndexJson) throws IOException, InterruptedException {
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'matrix-fixture'\n");
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'base'
                    id 'org.graalvm.internal.tck-harness'
                }
                """);
        Files.writeString(projectDir.resolve("LICENSE"), "test");
        Files.createDirectories(projectDir.resolve("tests/tck-build-logic"));
        Files.createDirectories(projectDir.resolve("tests/src/com.example/demo/1.0.0"));
        Files.createDirectories(projectDir.resolve("tests/src/com.example/demo/2.0.0"));
        Files.createDirectories(projectDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.createDirectories(projectDir.resolve("metadata/com.example/demo/2.0.0"));
        Files.writeString(projectDir.resolve("ci.json"), """
                {
                  "buildArgs": [],
                  "nativeImageModes": {
                    "current-defaults": []
                  },
                  "generateChangedMetadataTestMatrix": {
                    "java": ["25"],
                    "os": ["ubuntu-latest"],
                    "versions-per-job": 2
                  }
                }
                """);
        writeIndex(metadataIndexJson);
        writeMetadataFile("1.0.0", """
                {
                  "resources": []
                }
                """);
        runCommand("git", "init");
        runCommand("git", "config", "user.name", "TCK Test");
        runCommand("git", "config", "user.email", "tck-test@example.com");
    }

    private void writeIndex(String json) throws IOException {
        Files.writeString(projectDir.resolve("metadata/com.example/demo/index.json"), json);
    }

    private void writeMetadataFile(String version, String json) throws IOException {
        Files.writeString(projectDir.resolve("metadata/com.example/demo/" + version + "/reachability-metadata.json"), json);
    }

    private String commitAll(String message) throws IOException, InterruptedException {
        runCommand("git", "add", "-A");
        runCommand("git", "commit", "-m", message);
        return runCommand("git", "rev-parse", "HEAD").trim();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> matrixEntries(String baseCommit) {
        String output = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(
                        "generateChangedMetadataTestMatrix",
                        "-PbaseCommit=" + baseCommit,
                        "-PnewCommit=HEAD",
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
                .map(entry -> Map.of(
                        "coordinates", entry.get("coordinates"),
                        "versions", entry.get("versions"),
                        "batch", entry.get("batch")
                ))
                .toList();
    }

    private static Map<String, Object> matrixEntry(String coordinates, List<String> versions, String batch) {
        return Map.of(
                "coordinates", coordinates,
                "versions", versions,
                "batch", batch
        );
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
