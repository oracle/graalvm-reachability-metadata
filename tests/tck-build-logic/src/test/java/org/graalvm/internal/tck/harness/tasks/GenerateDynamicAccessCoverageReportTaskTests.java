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

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// §AR-test-harness.8: an empty coverage report must be a property of the library,
/// so a report input the native test build never produced fails the task, and stored
/// call sites replace the native listing only when they exist. §AR-test-harness.8.1: the
/// agent run covers the extension suite, and a failed one fails the task.
class GenerateDynamicAccessCoverageReportTaskTests {

    private static final String COORDINATE = "com.example:demo:1.0.0";

    private static final String CLASS_HELPER_FRAME = "com.example.Foo.class$(Foo.java:20)";

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

    @Test
    void storedCallSitesAreAbsentByDefault() throws IOException {
        GenerateDynamicAccessCoverageReportTask task = createTask();

        assertThat(task.storedCallSites()).isNull();
    }

    @Test
    void storedCallSitesComeFromTheProperty() throws IOException {
        GenerateDynamicAccessCoverageReportTask task = createTask();
        Path copy = Files.createDirectories(tempDir.resolve("runtime/call-sites"));
        task.getProject().getExtensions().getExtraProperties()
                .set(GenerateDynamicAccessCoverageReportTask.CALL_SITES_PROPERTY, copy.toString());

        assertThat(task.storedCallSites()).isEqualTo(copy.toAbsolutePath());
        assertThatCode(() -> task.requireDynamicAccessInput(COORDINATE, true, copy)).doesNotThrowAnyException();
    }

    @Test
    void missingStoredCallSitesFailTheTask() throws IOException {
        GenerateDynamicAccessCoverageReportTask task = createTask();
        Path copy = tempDir.resolve("runtime/missing-call-sites");

        assertThatThrownBy(() -> task.requireDynamicAccessInput(COORDINATE, true, copy))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining(copy.toString());
    }

    @Test
    void agentRunCoversTheExtensionSuite() throws IOException {
        RecordingTask task = createTask(RecordingTask.class);
        task.writesOrigins = true;

        Path originsOutput = task.maybeCollectAgentOrigins(COORDINATE, List.of(libraryJar()), writeCallSite(CLASS_HELPER_FRAME));

        assertThat(originsOutput).isEqualTo(agentOriginsDir());
        assertThat(task.invocations).singleElement().satisfies(arguments -> assertThat(arguments).containsExactly(
                "javaTest",
                "-Pcoordinates=" + COORDINATE,
                "-PincludeCodeCoverageSuite=true",
                "-PdynamicAccessOriginsOutput=" + agentOriginsDir()
        ));
    }

    @Test
    void failedAgentRunFailsTheTask() throws IOException {
        RecordingTask task = createTask(RecordingTask.class);
        task.exitCode = 1;
        Path callSites = writeCallSite(CLASS_HELPER_FRAME);

        assertThatThrownBy(() -> task.maybeCollectAgentOrigins(COORDINATE, List.of(libraryJar()), callSites))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining(COORDINATE)
                .hasMessageContaining("failed with exit code 1");
    }

    @Test
    void agentRunWithoutOriginsFailsTheTask() throws IOException {
        RecordingTask task = createTask(RecordingTask.class);
        Path callSites = writeCallSite(CLASS_HELPER_FRAME);

        assertThatThrownBy(() -> task.maybeCollectAgentOrigins(COORDINATE, List.of(libraryJar()), callSites))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("wrote no origins");
    }

    @Test
    void lineDecidedCallSitesNeverStartTheAgentRun() throws IOException {
        RecordingTask task = createTask(RecordingTask.class);
        task.exitCode = 1;

        Path originsOutput = task.maybeCollectAgentOrigins(
                COORDINATE, List.of(libraryJar()), writeCallSite("com.example.Foo.load(Foo.java:10)"));

        assertThat(originsOutput).isNull();
        assertThat(task.invocations).isEmpty();
    }

    private Path dynamicAccessDir() {
        return tempDir.resolve("tests/src/com.example/demo/1.0.0/build/native/nativeTestCompile/dynamic-access");
    }

    private Path agentOriginsDir() {
        return tempDir.resolve("tests/src/com.example/demo/1.0.0/build/reports/dynamic-access/agent-origins");
    }

    private Path libraryJar() throws IOException {
        Path jar = tempDir.resolve("demo.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("com/example/Foo.class"));
            output.write(new byte[]{0});
            output.closeEntry();
        }
        return jar;
    }

    /// Writes `frame` as the coordinate's only call site, beside a JaCoCo report that analyzed
    /// `Foo`, kept `load`, and filtered the synthetic `class$`.
    private Path writeCallSite(String frame) throws IOException {
        Path jacocoReport = tempDir.resolve(
                "tests/src/com.example/demo/1.0.0/build/reports/jacoco/jacocoCodeCoverageReport/jacocoCodeCoverageReport.xml");
        Files.createDirectories(jacocoReport.getParent());
        Files.writeString(jacocoReport, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <package name="com/example">
                    <class name="com/example/Foo" sourcefilename="Foo.java">
                      <method name="load" desc="()V" line="10"/>
                    </class>
                    <sourcefile name="Foo.java">
                      <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                  <counter type="INSTRUCTION" missed="0" covered="3"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """, StandardCharsets.UTF_8);
        Path callSites = Files.createDirectories(tempDir.resolve("call-sites/demo"));
        Files.writeString(callSites.resolve("reflection-calls.json"), """
                {"java.lang.Class#forName(java.lang.String)": ["%s"]}
                """.formatted(frame), StandardCharsets.UTF_8);
        return callSites.getParent();
    }

    private GenerateDynamicAccessCoverageReportTask createTask() throws IOException {
        return createTask(GenerateDynamicAccessCoverageReportTask.class);
    }

    private <T extends GenerateDynamicAccessCoverageReportTask> T createTask(Class<T> type) throws IOException {
        Files.createDirectories(tempDir.resolve("metadata"));
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/1.0.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.getExtensions().create("tck", TckExtension.class, project);
        T task = project.getTasks().create("generateDynamicAccessCoverageReport", type);
        assertThat(task).isNotNull();
        return task;
    }

    /// Records each nested Gradle invocation instead of running it; `writesOrigins` makes the
    /// agent run leave an origins file, as a successful one does.
    abstract static class RecordingTask extends GenerateDynamicAccessCoverageReportTask {
        final List<List<String>> invocations = new ArrayList<>();
        int exitCode;
        boolean writesOrigins;

        @Inject
        public RecordingTask() {
        }

        @Override
        protected CommandResult runGradle(List<String> arguments, boolean streamOutput) {
            invocations.add(arguments);
            if (writesOrigins) {
                try {
                    Files.writeString(agentOriginsFile(arguments), "{}");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            return new CommandResult(exitCode, "", "");
        }

        private static Path agentOriginsFile(List<String> arguments) {
            String prefix = "-PdynamicAccessOriginsOutput=";
            return arguments.stream()
                    .filter(argument -> argument.startsWith(prefix))
                    .map(argument -> Path.of(argument.substring(prefix.length()), "reachability-metadata-origins.txt"))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
