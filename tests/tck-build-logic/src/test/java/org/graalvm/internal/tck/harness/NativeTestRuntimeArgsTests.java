/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// §FS-tests.1: the run arguments a test project sets, on its test binary or on `nativeTest`,
/// reach the native run beside the harness's JUnit timeout arguments (§FS-test-contract.1.6).
class NativeTestRuntimeArgsTests {

    private static final String RUNTIME_ARGS_PREFIX = "nativeTestRuntimeArgs=";

    private static final List<String> JUNIT_TIMEOUT_PROPERTIES = List.of(
            "-Djunit.jupiter.execution.timeout.default",
            "-Djunit.jupiter.execution.timeout.mode",
            "-Djunit.jupiter.execution.timeout.thread.mode.default"
    );

    @TempDir
    Path repoRoot;

    @Test
    void keepsProjectRuntimeArgsBesideJUnitTimeoutArgs() throws IOException {
        writeFixture();

        List<String> runtimeArgs = nativeTestRuntimeArgs();

        assertThat(runtimeArgs).contains("-Dtck.binary.argument=kept", "-Dtck.task.argument=kept");
        assertThat(runtimeArgs.stream().map(runtimeArg -> runtimeArg.split("=", 2)[0]).toList())
                .containsAll(JUNIT_TIMEOUT_PROPERTIES);
    }

    /// Runs the fixture's coordinate test project and returns `nativeTest.runtimeArgs`.
    private List<String> nativeTestRuntimeArgs() {
        String output = GradleRunner.create()
                .withProjectDir(projectDir().toFile())
                .withPluginClasspath()
                .withArguments("printNativeTestRuntimeArgs", "--offline", "--quiet")
                .build()
                .getOutput();
        String runtimeArgsLine = output.lines()
                .filter(line -> line.startsWith(RUNTIME_ARGS_PREFIX))
                .findFirst()
                .orElseThrow();
        return List.of(runtimeArgsLine.substring(RUNTIME_ARGS_PREFIX.length()).split("\\|"));
    }

    /// Writes a minimal repository holding one coordinate test project that sets a run argument
    /// on its test binary and one on `nativeTest`, the two ways test projects pass them.
    private void writeFixture() throws IOException {
        Files.writeString(repoRoot.resolve("LICENSE"), "test");
        Files.createDirectories(repoRoot.resolve("metadata/com.example/demo/1.0.0"));
        Files.createDirectories(repoRoot.resolve("tests/tck-build-logic"));
        Files.writeString(repoRoot.resolve("ci.json"), """
                {
                  "buildArgs": [],
                  "nativeImageModes": {
                    "current-defaults": []
                  }
                }
                """);
        Files.createDirectories(repoRoot.resolve("gradle"));
        Files.writeString(repoRoot.resolve("gradle/libs.versions.toml"), """
                [versions]
                scala2 = "2.13.17"
                scala3 = "3.7.4"

                [libraries]
                test-junit-bom = { module = "org.junit:junit-bom", version = "5.9.2" }
                test-junit-jupiter-core = { module = "org.junit.jupiter:junit-jupiter" }
                test-junit-vintage = { module = "org.junit.vintage:junit-vintage-engine" }
                """);

        Path projectDir = projectDir();
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("settings.gradle"), """
                dependencyResolutionManagement {
                    versionCatalogs {
                        libs {
                            from(files("../../../../../gradle/libs.versions.toml"))
                        }
                    }
                }

                rootProject.name = "com.example.demo_tests"
                """);
        Files.writeString(projectDir.resolve("gradle.properties"), """
                library.coordinates = com.example:demo:1.0.0
                library.version = 1.0.0
                metadata.dir = com.example/demo/1.0.0/
                """);
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id "org.graalvm.internal.tck"
                }

                // Reading runtimeArgs resolves the test classpath: keep it empty and offline.
                configurations.configureEach {
                    withDependencies { it.clear() }
                }

                graalvmNative {
                    binaries {
                        test {
                            runtimeArgs("-Dtck.binary.argument=kept")
                        }
                    }
                }

                tasks.named("nativeTest") {
                    runtimeArgs.add("-Dtck.task.argument=kept")
                }

                tasks.register("printNativeTestRuntimeArgs") {
                    Provider<List<String>> runtimeArgs = tasks.named("nativeTest")
                            .flatMap { it.runtimeArgs }
                    doLast {
                        println("%s" + runtimeArgs.get().join("|"))
                    }
                }
                """.formatted(RUNTIME_ARGS_PREFIX));
    }

    private Path projectDir() {
        return repoRoot.resolve("tests/src/com.example/demo/1.0.0");
    }
}
