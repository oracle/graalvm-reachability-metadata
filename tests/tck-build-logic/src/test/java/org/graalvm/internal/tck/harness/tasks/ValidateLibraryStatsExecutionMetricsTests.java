/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.Project;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateLibraryStatsExecutionMetricsTests extends ValidateLibraryStatsTestSupport {

    @Test
    void validateAcceptsExecutionMetricsForExistingTestedVersion() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
                      "libraryCoverage": {
                        "instruction": "N/A",
                        "line": "N/A",
                        "method": "N/A"
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile("com.example", "demo", "1.0.0", "com.example:demo:1.0.0", "1.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatCode(task::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsExecutionMetricsForUnknownLibrary() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
                      "libraryCoverage": {
                        "instruction": "N/A",
                        "line": "N/A",
                        "method": "N/A"
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile("com.example", "other", "1.0.0", "com.example:other:1.0.0", "1.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Execution metrics library has no matching metadata index")
                .hasMessageContaining("com.example:other:1.0.0");
    }

    @Test
    void validateRejectsExecutionMetricsForUntestedVersion() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
                      "libraryCoverage": {
                        "instruction": "N/A",
                        "line": "N/A",
                        "method": "N/A"
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile("com.example", "demo", "2.0.0", "com.example:demo:2.0.0", "2.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Execution metrics library version is not listed in metadata index tested-versions")
                .hasMessageContaining("com.example:demo:2.0.0");
    }

    @Test
    void validateRejectsExecutionMetricsStatsVersionMismatch() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
                      "libraryCoverage": {
                        "instruction": "N/A",
                        "line": "N/A",
                        "method": "N/A"
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile("com.example", "demo", "1.0.0", "com.example:demo:1.0.0", "2.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Execution metrics stats.version mismatch")
                .hasMessageContaining("expected 1.0.0")
                .hasMessageContaining("found 2.0.0");
    }

    @Test
    void validateRejectsExecutionMetricsForUntestedPreviousLibraryVersion() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
                      "libraryCoverage": {
                        "instruction": "N/A",
                        "line": "N/A",
                        "method": "N/A"
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile(
                "com.example",
                "demo",
                "1.0.0",
                "com.example:demo:1.0.0",
                "1.0.0",
                "com.example:demo:0.9.0",
                "0.9.0"
        );

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Execution metrics previous_library version is not listed in metadata index tested-versions")
                .hasMessageContaining("com.example:demo:0.9.0");
    }
}
