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
import org.graalvm.internal.tck.utils.BaseLayerUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestInvocationTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void commandForForwardsMetadataConfigDirs() throws IOException {
        Project project = createProject();
        project.getExtensions().getExtraProperties().set("metadataConfigDirs", "/tmp/config-0,/tmp/config-1");

        TestInvocationTask task = project.getTasks().create("nativeTest", TestInvocationTask.class);

        List<String> command = task.commandFor("com.example:demo:1.0.0");

        assertThat(command)
                .contains("nativeTest")
                .contains("-PmetadataConfigDirs=/tmp/config-0,/tmp/config-1");
    }

    @Test
    void commandForOmitsMetadataConfigDirsWhenPropertyUnset() throws IOException {
        Project project = createProject();

        TestInvocationTask task = project.getTasks().create("nativeTest", TestInvocationTask.class);

        List<String> command = task.commandFor("com.example:demo:1.0.0");

        assertThat(command).contains("nativeTest");
        assertThat(command).noneMatch(arg -> arg.startsWith("-PmetadataConfigDirs="));
    }

    @Test
    void dedicatedLayerCommandUsesCoordinateSpecificLayer() throws IOException {
        Project project = createProject();
        Path layerRoot = tempDir.resolve("library-layers");
        project.getExtensions().getExtraProperties().set("tck.dedicatedLayerRoot", layerRoot.toString());
        DedicatedLayerTestInvocationTask task = project.getTasks().create(
                "dedicatedLayerTest", DedicatedLayerTestInvocationTask.class);

        List<String> command = task.commandFor("com.example:demo:1.0.0");

        assertThat(command).contains("-Ptck.dedicatedLayer=true");
        assertThat(command).anyMatch(argument -> argument.startsWith(
                "-Ptck.baseLayerFile=" + layerRoot.toAbsolutePath()));
        assertThat(command).anyMatch(argument -> argument.endsWith("/libDedicated.nil"));
    }

    @Test
    void dedicatedLayerTaskDeletesLayerWhenRequested() throws IOException {
        Project project = createProject();
        Path layerRoot = tempDir.resolve("library-layers");
        project.getExtensions().getExtraProperties().set("tck.dedicatedLayerRoot", layerRoot.toString());
        project.getExtensions().getExtraProperties().set("tck.layered.deleteDedicatedLayerAfterTest", "true");
        DedicatedLayerTestInvocationTask task = project.getTasks().create(
                "dedicatedLayerTest", DedicatedLayerTestInvocationTask.class);
        File baseLayerFile = BaseLayerUtils.resolveDedicatedLayerFile(
                project, "com.example:demo:1.0.0");
        Files.createDirectories(baseLayerFile.toPath().getParent());
        Files.writeString(baseLayerFile.toPath(), "layer");

        task.afterEach("com.example:demo:1.0.0");

        assertThat(baseLayerFile.getParentFile()).doesNotExist();
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
