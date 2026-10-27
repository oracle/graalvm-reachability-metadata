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
import org.graalvm.internal.tck.stats.LibraryStatsSupport;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared project skeleton, stats-tree fixtures, and task stub for
 * `ValidateLibraryStatsTask` tests.
 */
abstract class ValidateLibraryStatsTestSupport {

    @TempDir
    Path tempDir;

    Project createProjectSkeleton() throws IOException {
        Files.createDirectories(tempDir.resolve("metadata"));
        Files.createDirectories(tempDir.resolve("tests"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.createDirectories(tempDir.resolve("stats/schemas"));
        Files.writeString(tempDir.resolve("LICENSE"), "test", StandardCharsets.UTF_8);
        Files.writeString(
                tempDir.resolve("stats/schemas/library-stats-schema-v1.0.2.json"),
                Files.readString(
                        locateRepoFile("stats/schemas/library-stats-schema-v1.0.2.json"),
                        StandardCharsets.UTF_8
                ),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                tempDir.resolve("stats/schemas/run-metrics-output-schema-v1.0.1.json"),
                Files.readString(
                        locateRepoFile("stats/schemas/run-metrics-output-schema-v1.0.1.json"),
                        StandardCharsets.UTF_8
                ),
                StandardCharsets.UTF_8
        );

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.getExtensions().create("tck", TckExtension.class, project);
        return project;
    }

    void createMetadataVersion(String groupId, String artifactId, String metadataVersion) throws IOException {
        Path artifactRoot = tempDir.resolve("metadata").resolve(groupId).resolve(artifactId);
        Files.createDirectories(artifactRoot.resolve(metadataVersion));
        Files.writeString(
                artifactRoot.resolve("index.json"),
                """
                [
                  {
                    "allowed-packages": [
                      "com.example"
                    ],
                    "metadata-version": "%s",
                    "tested-versions": [
                      "%s"
                    ]
                  }
                ]
                """.formatted(metadataVersion, metadataVersion),
                StandardCharsets.UTF_8
        );
    }

    void writeStatsFile(String groupId, String artifactId, String metadataVersion, String statsJson) throws IOException {
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), groupId, artifactId, metadataVersion);
        Files.createDirectories(statsFile.getParent());
        Files.writeString(statsFile, statsJson, StandardCharsets.UTF_8);
    }

    void writeExecutionMetricsFile(
            String groupId,
            String artifactId,
            String version,
            String library,
            String statsVersion
    ) throws IOException {
        writeExecutionMetricsFile(groupId, artifactId, version, library, statsVersion, null, null);
    }

    void writeExecutionMetricsFile(
            String groupId,
            String artifactId,
            String version,
            String library,
            String statsVersion,
            String previousLibrary,
            String previousLibraryStatsVersion
    ) throws IOException {
        Path metricsFile = tempDir.resolve("stats")
                .resolve(groupId)
                .resolve(artifactId)
                .resolve(version)
                .resolve("execution-metrics.json");
        Files.createDirectories(metricsFile.getParent());
        String previousLibraryFields = "";
        if (previousLibrary != null) {
            previousLibraryFields = """
                    "previous_library": "%s",
                    "previous_library_stats": {
                      "version": "%s"
                    },
                """.formatted(previousLibrary, previousLibraryStatsVersion);
        }
        Files.writeString(
                metricsFile,
                """
                {
                  "add_new_library_support:2026-04-27": {
                    "artifacts": {
                      "metadata_file": "metadata/%s/%s/%s/reachability-metadata.json",
                      "test_file": "tests/src/%s/%s/%s/src/test/java/Test.java"
                    },
                    "library": "%s",
                %s
                    "metrics": {
                      "code_coverage_percent": 0.0,
                      "cost_usd": 0.0,
                      "input_tokens_used": 0,
                      "iterations": 0,
                      "metadata_entries": 0,
                      "output_tokens_used": 0,
                      "tested_library_loc": 0
                    },
                    "stats": {
                      "version": "%s"
                    },
                    "status": "success",
                    "strategy_name": "test",
                    "timestamp": "2026-04-27T20:43:22.869870Z"
                  }
                }
                """.formatted(
                        groupId,
                        artifactId,
                        version,
                        groupId,
                        artifactId,
                        version,
                        library,
                        previousLibraryFields,
                        statsVersion
                ),
                StandardCharsets.UTF_8
        );
    }

    Path locateRepoFile(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 10; i++) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }
        throw new IOException("Failed to locate repository file " + relativePath);
    }

    abstract static class TestValidateLibraryStatsTask extends ValidateLibraryStatsTask {
        @Inject
        public TestValidateLibraryStatsTask() {
        }
    }
}
