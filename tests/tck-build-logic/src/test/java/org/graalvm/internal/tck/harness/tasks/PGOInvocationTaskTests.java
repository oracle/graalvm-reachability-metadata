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

/** Tests the root-to-coordinate PGO property contract (§TCK-test-harness.8). */
class PGOInvocationTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void samplingBuildForwardsSamplingPeriod() throws IOException {
        Project project = createProject();
        project.getExtensions().getExtraProperties().set("pgoSamplingPeriodMicros", "100");

        NativeTestPGOSamplingInvocationTask task = project.getTasks().create(
                "nativeTestPGOSampling",
                NativeTestPGOSamplingInvocationTask.class
        );

        List<String> command = task.commandFor("com.example:demo:1.0.0");

        assertThat(command)
                .contains("nativeTestPGOSampling")
                .contains("-PpgoSamplingPeriodMicros=100");
    }

    @Test
    void samplingRunForwardsProfilePathAndSamplingPeriod() throws IOException {
        Project project = createProject();
        project.getExtensions().getExtraProperties().set("pgoProfilePath", "/tmp/profile.iprof");
        project.getExtensions().getExtraProperties().set("pgoSamplingPeriodMicros", "250");

        RunNativeTestPGOInvocationTask task = project.getTasks().create(
                "runNativeTestPGO",
                RunNativeTestPGOInvocationTask.class
        );

        List<String> command = task.commandFor("com.example:demo:1.0.0");

        assertThat(command)
                .contains("runNativeTestPGO")
                .contains("-PpgoProfilePath=/tmp/profile.iprof")
                .contains("-PpgoSamplingPeriodMicros=250");
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
