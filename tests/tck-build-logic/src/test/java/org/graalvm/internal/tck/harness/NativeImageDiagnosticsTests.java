/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import groovy.json.JsonSlurper;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Keeps tracing and profiling test-only across component boundaries. §FS-tests.3.2
@EnabledOnOs(OS.LINUX)
class NativeImageDiagnosticsTests {
    @TempDir
    Path tempDir;

    @Test
    void tracingStaysOnTheTestExecutable() throws IOException {
        Path project = LayerBuildOptionsTestFixture.create(tempDir, "", true);
        Map<String, List<String>> options = options(runner(project, "runNativeTraceImage",
                "-PtraceMetadataPath=" + tempDir.resolve("trace")).build());

        assertThat(options.get("test")).contains("-H:+MetadataTracingSupport",
                "--exact-reachability-metadata", "-H:MissingRegistrationReportingMode=Exit");
        assertThat(options.get("base")).doesNotContain("-H:+MetadataTracingSupport",
                "--exact-reachability-metadata", "-H:MissingRegistrationReportingMode=Exit");
    }

    @Test
    void profilingStaysOnTheTestExecutable() throws IOException {
        Path project = LayerBuildOptionsTestFixture.create(tempDir, "", true);
        fakeNativeImage("PGOSamplingPeriodMicros");
        Map<String, List<String>> options = options(runner(project, "nativeTestPGOSampling",
                "-PpgoSamplingPeriodMicros=200").build());

        List<String> instrumentation = List.of("--pgo-instrument", "--pgo-sampling",
                "-H:PGOSamplingPeriodMicros=200", "-H:+PrintAnalysisCallTree", "-H:PrintAnalysisCallTreeType=CSV");
        assertThat(options.get("test")).containsAll(instrumentation);
        assertThat(options.get("base")).doesNotContainAnyElementsOf(instrumentation);
    }

    @Test
    void unsupportedCustomSamplingPeriodStillFails() throws IOException {
        Path project = LayerBuildOptionsTestFixture.create(tempDir, "", true);
        fakeNativeImage("other expert options");
        BuildResult result = runner(project, "nativeTestPGOSampling", "-PpgoSamplingPeriodMicros=200").buildAndFail();

        assertThat(result.getOutput()).contains("it samples at a fixed period, so a period of 200 cannot be applied");
    }

    private void fakeNativeImage(String expertOptions) throws IOException {
        Path tool = Files.createDirectories(tempDir.resolve("graal/bin")).resolve("native-image");
        Files.writeString(tool, "#!/bin/sh\necho '" + expertOptions + "'\n");
        assertThat(tool.toFile().setExecutable(true)).isTrue();
    }

    private GradleRunner runner(Path project, String requestedTask, String property) {
        List<String> arguments = new ArrayList<>(List.of(requestedTask, "inspectLayerOptions",
                "-x", requestedTask, property, "--offline", "--quiet"));
        Map<String, String> environment = new HashMap<>(System.getenv());
        if (Files.exists(tempDir.resolve("graal"))) {
            environment.put("GRAALVM_HOME", tempDir.resolve("graal").toString());
        }
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withEnvironment(environment).withArguments(arguments);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> options(BuildResult result) {
        String json = result.getOutput().lines().filter(line -> line.startsWith("layerOptions="))
                .findFirst().orElseThrow().substring("layerOptions=".length());
        return (Map<String, List<String>>) new JsonSlurper().parseText(json);
    }
}
