/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import groovy.json.JsonSlurper;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises the coordinate DSL through the real convention plugin. §FS-tests.3.2
class DedicatedLayerBuildOptionsTests {
    @TempDir
    Path tempDir;

    @Test
    void sharesTestOnlyOptionsAndRetainsLazyBaseConfiguration() throws IOException {
        Map<String, List<String>> arguments = argumentsFor("""
                test {
                    buildArgs.add('-H:+AllowDeprecatedBuilderClassesOnImageClasspath')
                    buildArgs.add('-H:+AddAllCharsets')
                    buildArgs.add('--initialize-at-build-time=org.slf4j.helpers.NOPLoggerFactory')
                    buildArgs.add('--future-defaults=run-time-initialize-file-system-providers')
                }
                """);

        for (String option : List.of(
                "-H:+AllowDeprecatedBuilderClassesOnImageClasspath", "-H:+AddAllCharsets",
                "--initialize-at-build-time=org.slf4j.helpers.NOPLoggerFactory",
                "--future-defaults=run-time-initialize-file-system-providers")) {
            assertThat(arguments.get("base")).containsOnlyOnce(option);
            assertThat(arguments.get("test")).containsOnlyOnce(option);
        }
        assertThat(arguments.get("base")).anyMatch(argument -> argument.endsWith("/late-config"));
        assertThat(arguments.get("base")).noneMatch(argument -> argument.contains("/metadata/com.example"));
        assertThat(arguments.get("test")).anyMatch(argument -> argument.contains("/metadata/com.example"));
        assertThat(arguments.get("base")).anyMatch(argument -> argument.contains("dedicatedLayerJUnitTestList"));
    }

    @Test
    void doesNotDuplicateBinariesAllOptions() throws IOException {
        Map<String, List<String>> arguments = argumentsFor("""
                all {
                    buildArgs.add('--add-opens=java.base/java.lang=ALL-UNNAMED')
                    buildArgs.add('-H:+AddAllCharsets')
                }
                """);

        assertThat(arguments.get("base")).containsOnlyOnce(
                "--add-opens=java.base/java.lang=ALL-UNNAMED", "-H:+AddAllCharsets", "--verbose");
        assertThat(arguments.get("test")).containsOnlyOnce(
                "--add-opens=java.base/java.lang=ALL-UNNAMED", "-H:+AddAllCharsets", "--verbose");
    }

    @Test
    void preservesOptionScopesWhenTestAndAllSettingsAreInterleaved() throws IOException {
        Map<String, List<String>> arguments = argumentsFor("""
                test {
                    buildArgs.addAll('-H:+UnlockExperimentalVMOptions',
                            '-H:+AllowJRTFileSystem', '-H:-UnlockExperimentalVMOptions')
                }
                all {
                    buildArgs.addAll('-H:+UnlockExperimentalVMOptions',
                            '-H:+AddAllCharsets', '-H:-UnlockExperimentalVMOptions')
                }
                """);

        for (String binary : List.of("base", "test")) {
            List<String> scopes = arguments.get(binary).stream()
                    .filter(argument -> argument.contains("UnlockExperimentalVMOptions")
                            || argument.contains("AllowJRTFileSystem") || argument.contains("AddAllCharsets"))
                    .toList();
            assertThat(scopes).containsExactly(
                    "-H:+UnlockExperimentalVMOptions", "-H:+AllowJRTFileSystem", "-H:-UnlockExperimentalVMOptions",
                    "-H:+UnlockExperimentalVMOptions", "-H:+AddAllCharsets", "-H:-UnlockExperimentalVMOptions");
        }
    }

    @Test
    void keepsGeneratedTestModulesOutOfTheBase() throws IOException {
        Map<String, List<String>> arguments = argumentsFor("""
                test {
                    buildArgs.addAll('--module-path', '/test/fixture.jar')
                    buildArgs.addAll('--add-modules', 'java.xml,com.example.fixture')
                    buildArgs.add('-H:+AllowJRTFileSystem')
                }
                """);

        assertThat(arguments.get("test")).contains("--module-path", "/test/fixture.jar",
                "java.xml,com.example.fixture");
        assertThat(arguments.get("base")).doesNotContain("--module-path", "/test/fixture.jar",
                "java.xml,com.example.fixture");
        assertThat(arguments.get("base")).contains("--add-modules", "java.xml", "-H:+AllowJRTFileSystem");
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> argumentsFor(String binaries) throws IOException {
        Path project = LayerBuildOptionsTestFixture.create(tempDir, binaries, true);
        String output = GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments("inspectLayerOptions", "--offline", "--quiet")
                .build().getOutput();
        String json = output.lines().filter(line -> line.startsWith("layerOptions="))
                .findFirst().orElseThrow().substring("layerOptions=".length());
        return (Map<String, List<String>>) new JsonSlurper().parseText(json);
    }
}
