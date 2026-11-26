/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.graalvm.internal.tck.harness.TckExtension;
import org.graalvm.internal.tck.stats.LibraryStatsModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// §AR-test-harness.8: the stats write `N/A` only for a figure that cannot exist, and a
/// failed dynamic-access report build fails the task instead.
class GenerateLibraryStatsTaskTests {

    private static final String COORDINATE = "com.example:demo:1.0.0";

    @TempDir
    Path tempDir;

    @Test
    void failedReportBuildFailsTheTask() throws IOException {
        RecordingTask task = createTask(libraryJar("com/example/Foo.class"));
        task.failingTask = "generateDynamicAccessReport";
        // A valid JaCoCo report, so only the failed report build can fail the task.
        Path jacocoReport = Files.createDirectories(tempDir.resolve(
                "tests/src/com.example/demo/1.0.0/build/reports/jacoco/jacocoCodeCoverageReport"))
                .resolve("jacocoCodeCoverageReport.xml");
        Files.writeString(jacocoReport, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="INSTRUCTION" missed="1" covered="2"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """);

        assertThatThrownBy(() -> task.computeVersionStats(COORDINATE))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("Dynamic access report generation failed for " + COORDINATE);
    }

    @Test
    void jarsWithoutBytecodeHaveNoLibraryCoverageAndNoDynamicAccess() throws IOException {
        RecordingTask task = createTask(libraryJar("META-INF/MANIFEST.MF"));
        task.failingTask = "generateDynamicAccessReport";

        LibraryStatsModels.VersionStats stats = task.computeVersionStats(COORDINATE);

        assertThat(stats.libraryCoverage().line().isAvailable()).isFalse();
        assertThat(stats.dynamicAccess().isAvailable()).isTrue();
        assertThat(stats.dynamicAccess().totalCalls()).isZero();
    }

    private Path libraryJar(String entry) throws IOException {
        Path jar = tempDir.resolve("demo.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(entry));
            output.write(new byte[]{0});
            output.closeEntry();
        }
        return jar;
    }

    private RecordingTask createTask(Path libraryJar) throws IOException {
        Files.createDirectories(tempDir.resolve("metadata"));
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/1.0.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.getExtensions().create("tck", TckExtension.class, project);
        RecordingTask task = project.getTasks().create("generateLibraryStats", RecordingTask.class);
        task.libraryJar = libraryJar;
        return task;
    }

    /// Answers each nested Gradle invocation instead of running it: `listLibraryJars` lists
    /// `libraryJar`, and `failingTask` exits `1`.
    abstract static class RecordingTask extends GenerateLibraryStatsTask {
        Path libraryJar;
        String failingTask;

        @Inject
        public RecordingTask() {
        }

        @Override
        protected CommandResult runGradle(List<String> arguments, boolean streamOutput) {
            if (arguments.contains("listLibraryJars")) {
                return new CommandResult(0, libraryJar.toString(), "");
            }
            return new CommandResult(arguments.contains(failingTask) ? 1 : 0, "", "");
        }
    }
}
