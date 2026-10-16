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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealUseTasksTests {

    @TempDir
    Path tempDir;

    @Test
    void selectsOnlyCoordinatesWithARealUseProject() throws IOException {
        Project project = createProject();
        Path realUse = Files.createDirectories(tempDir.resolve("real-use-tests/src/com.example/demo/1.0.1"));
        TckExtension tck = project.getExtensions().getByType(TckExtension.class);

        assertThat(tck.getMatchingRealUseCoordinates("all")).containsExactly("com.example:demo:1.0.1");
        assertThat(tck.getMatchingRealUseCoordinates("com.example:demo:1.0.0")).isEmpty();
        assertThat(tck.getMatchingRealUseCoordinates("1/2")).containsExactly("com.example:demo:1.0.1");
        assertThat(tck.getMatchingRealUseCoordinates("2/2")).isEmpty();

        RealUseJavaTestInvocationTask task = project.getTasks().create("realUseJavaTest", RealUseJavaTestInvocationTask.class);
        assertThat(task.workingDirsFor("com.example:demo:1.0.1")).containsExactly(realUse);
        assertThat(task.failWhenFilterSelectsNothing()).isFalse();
    }

    @Test
    void javaTestCollectsAgentMetadataOnlyWhenAsked() throws IOException {
        Project project = createProject();
        RealUseJavaTestInvocationTask task = project.getTasks().create("realUseJavaTest", RealUseJavaTestInvocationTask.class);
        assertThat(task.commandFor("com.example:demo:1.0.0")).endsWith("test");

        Path output = tempDir.resolve("agent-output");
        project.getExtensions().getExtraProperties().set("tck.realUse.agentOutput", output.toString());
        assertThat(task.commandFor("com.example:demo:1.0.0"))
                .contains("-Pagent", "test", "copyRealUseAgentMetadata", "-Ptck.realUse.agentOutput=" + output);

        project.getExtensions().getExtraProperties().set("tck.realUse.agentOutput", "relative/dir");
        assertThatThrownBy(() -> task.commandFor("com.example:demo:1.0.0")).isInstanceOf(GradleException.class);
    }

    @Test
    void jacocoReportRunsTheProjectReport() throws IOException {
        Project project = createProject();
        RealUseJacocoReportInvocationTask task = project.getTasks().create("realUseJacocoReport", RealUseJacocoReportInvocationTask.class);

        assertThat(task.commandFor("com.example:demo:1.0.0")).endsWith("jacocoTestReport");
    }

    @Test
    void nativeTestForwardsExactReachability() throws IOException {
        Project project = createProject();
        project.getExtensions().getExtraProperties().set("tck.realUse.exactReachability", "true");
        RealUseNativeTestInvocationTask task = project.getTasks().create("realUseNativeTest", RealUseNativeTestInvocationTask.class);

        assertThat(task.commandFor("com.example:demo:1.0.0"))
                .contains("nativeTest", "-Ptck.realUse.exactReachability=true");
    }

    @Test
    void scaffoldCreatesTheProjectOnceForASupportedCoordinate() throws IOException {
        Project project = createProject();
        project.getExtensions().getExtraProperties().set("coordinates", "com.example:demo:1.0.1");
        ScaffoldRealUseTestTask task = project.getTasks().create("scaffoldRealUseTest", ScaffoldRealUseTestTask.class);

        task.scaffold();

        Path realUse = tempDir.resolve("real-use-tests/src/com.example/demo/1.0.1");
        assertThat(Files.readString(realUse.resolve("build.gradle")))
                .contains("testImplementation \"com.example:demo:$libraryVersion\"")
                .contains("userCodeFilterPath = \"user-code-filter.json\"")
                .doesNotContain("assertj");
        assertThat(Files.readString(realUse.resolve("settings.gradle"))).contains("../../../../../tests/tck-build-logic");
        assertThat(Files.readString(realUse.resolve("gradle.properties"))).contains("library.version = 1.0.1");
        assertThat(realUse.resolve(".gitignore")).isRegularFile();
        assertThat(realUse.resolve("src/test/java")).isDirectory();
        assertThatThrownBy(task::scaffold).isInstanceOf(GradleException.class).hasMessageContaining("already exists");
    }

    @Test
    void scaffoldUsesTheLanguageOfTheCoordinatesIndexEntry() throws IOException {
        Project project = createProject();
        writeIndex("""
                "language": {"name": "kotlin", "version": "2.2"},
                """);
        project.getExtensions().getExtraProperties().set("coordinates", "com.example:demo:1.0.1");

        project.getTasks().create("scaffoldRealUseTest", ScaffoldRealUseTestTask.class).scaffold();

        Path realUse = tempDir.resolve("real-use-tests/src/com.example/demo/1.0.1");
        assertThat(Files.readString(realUse.resolve("build.gradle")))
                .contains("alias(libs.plugins.kotlin.jvm)")
                .contains("testImplementation \"com.example:demo:$libraryVersion\"")
                .contains("userCodeFilterPath = \"user-code-filter.json\"")
                .doesNotContain("assertj");
        assertThat(realUse.resolve("src/test/kotlin")).isDirectory();
        assertThat(realUse.resolve("src/test/java")).doesNotExist();
    }

    @Test
    void scaffoldUsesTheScalaVersionOfTheIndexEntry() throws IOException {
        Project project = createProject();
        writeIndex("""
                "language": {"name": "scala", "version": "3"},
                """);
        project.getExtensions().getExtraProperties().set("coordinates", "com.example:demo:1.0.0");

        project.getTasks().create("scaffoldRealUseTest", ScaffoldRealUseTestTask.class).scaffold();

        Path realUse = tempDir.resolve("real-use-tests/src/com.example/demo/1.0.0");
        assertThat(Files.readString(realUse.resolve("build.gradle")))
                .contains("id \"scala\"")
                .contains("scala3-library_3")
                .doesNotContain("assertj");
        assertThat(realUse.resolve("src/test/scala")).isDirectory();
    }

    private Project createProject() throws IOException {
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        writeIndex("");
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/1.0.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.getExtensions().create("tck", TckExtension.class, project);
        return project;
    }

    /// Writes the library index with one entry for 1.0.0 and 1.0.1, carrying `languageField` verbatim.
    private void writeIndex(String languageField) throws IOException {
        Files.writeString(tempDir.resolve("metadata/com.example/demo/index.json"), """
                [
                  {
                    %s"metadata-version": "1.0.0",
                    "tested-versions": [
                      "1.0.0",
                      "1.0.1"
                    ]
                  }
                ]
                """.formatted(languageField));
    }
}
