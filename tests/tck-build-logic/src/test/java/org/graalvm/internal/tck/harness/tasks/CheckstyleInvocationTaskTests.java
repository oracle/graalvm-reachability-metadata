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

import static org.assertj.core.api.Assertions.assertThat;

class CheckstyleInvocationTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void checksTheRealUseProjectAfterTheTestsProject() throws IOException {
        CheckstyleInvocationTask task = createTask();
        Path realUse = Files.createDirectories(tempDir.resolve("real-use-tests/src/com.example/demo/1.0.0"));

        assertThat(task.workingDirsFor("com.example:demo:1.0.0"))
                .containsExactly(tempDir.resolve("tests/src/com.example/demo/1.0.0"), realUse);
    }

    @Test
    void checksOnlyTheTestsProjectWithoutARealUseProject() throws IOException {
        CheckstyleInvocationTask task = createTask();

        assertThat(task.workingDirsFor("com.example:demo:1.0.0"))
                .containsExactly(tempDir.resolve("tests/src/com.example/demo/1.0.0"));
    }

    private CheckstyleInvocationTask createTask() throws IOException {
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(tempDir.resolve("metadata/com.example/demo/index.json"), """
                [
                  {
                    "metadata-version": "1.0.0",
                    "tested-versions": [
                      "1.0.0"
                    ]
                  }
                ]
                """);
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/1.0.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.getExtensions().create("tck", TckExtension.class, project);
        return project.getTasks().create("checkstyle", CheckstyleInvocationTask.class);
    }
}
