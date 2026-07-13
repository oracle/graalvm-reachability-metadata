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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests dedicated coverage-suite forwarding (§TCK-test-harness.8,
 * §forge/WF-code-coverage-improvement.3.1).
 */
class CodeCoverageSuiteInvocationTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void coverageWorkflowTasksForwardDedicatedSuitePath() throws IOException {
        Project project = createProject();
        String suitePath = tempDir.resolve("coverage-suite").toString();
        project.getExtensions().getExtraProperties().set("codeCoverageSuitePath", suitePath);

        List<AllCoordinatesExecTask> tasks = List.of(
                project.getTasks().create("coverageCompile", CompileTestJavaInvocationTask.class),
                project.getTasks().create("coverageJava", JavaTestInvocationTask.class),
                project.getTasks().create("coverageJacoco", JacocoTestReportInvocationTask.class),
                project.getTasks().create("coverageStyle", CheckstyleInvocationTask.class),
                project.getTasks().create("coverageNativeCompile", NativeTestCompileInvocationTask.class),
                project.getTasks().create("coveragePgoBuild", NativeTestPGOSamplingInvocationTask.class),
                project.getTasks().create("coveragePgoRun", RunNativeTestPGOInvocationTask.class),
                project.getTasks().create("coverageNativeRun", TestInvocationTask.class)
        );

        for (AllCoordinatesExecTask task : tasks) {
            assertThat(task.commandFor("com.example:demo:1.0.0"))
                    .as(task.getName())
                    .contains("-PcodeCoverageSuitePath=" + suitePath);
        }
    }

    private Project createProject() throws IOException {
        Files.createDirectories(tempDir.resolve("metadata"));
        Files.createDirectories(tempDir.resolve("tests/src"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.getExtensions().create("tck", TckExtension.class, project);
        return project;
    }
}
