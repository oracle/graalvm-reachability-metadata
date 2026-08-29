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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// §AR-test-harness.8: an empty coverage report must be a property of the library,
/// so a report input the native test build never produced fails the task.
class GenerateDynamicAccessCoverageReportTaskTests {

    private static final String COORDINATE = "com.example:demo:1.0.0";

    @TempDir
    Path tempDir;

    @Test
    void producedDynamicAccessInputIsAccepted() throws IOException {
        GenerateDynamicAccessCoverageReportTask task = createTask();
        Files.createDirectories(dynamicAccessDir());

        assertThatCode(() -> task.requireDynamicAccessInput(COORDINATE, true)).doesNotThrowAnyException();
    }

    @Test
    void missingDynamicAccessDirectoryFailsTheTask() throws IOException {
        GenerateDynamicAccessCoverageReportTask task = createTask();

        assertThatThrownBy(() -> task.requireDynamicAccessInput(COORDINATE, true))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining(COORDINATE)
                .hasMessageContaining(dynamicAccessDir().toString());
    }

    @Test
    void failedDynamicAccessGenerationFailsTheTask() throws IOException {
        GenerateDynamicAccessCoverageReportTask task = createTask();
        Files.createDirectories(dynamicAccessDir());

        assertThatThrownBy(() -> task.requireDynamicAccessInput(COORDINATE, false))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("was not produced");
    }

    private Path dynamicAccessDir() {
        return tempDir.resolve("tests/src/com.example/demo/1.0.0/build/native/nativeTestCompile/dynamic-access");
    }

    private GenerateDynamicAccessCoverageReportTask createTask() throws IOException {
        Files.createDirectories(tempDir.resolve("metadata"));
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/1.0.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.getExtensions().create("tck", TckExtension.class, project);
        GenerateDynamicAccessCoverageReportTask task = project.getTasks().create(
                "generateDynamicAccessCoverageReport",
                GenerateDynamicAccessCoverageReportTask.class
        );
        assertThat(task).isNotNull();
        return task;
    }
}
