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
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Proves automatic base selection and lack of cross-invocation reuse. §FS-tests.3.3
@EnabledOnOs(OS.LINUX)
class CustomSharedLayerTests {
    @TempDir
    Path tempDir;

    @Test
    void commonPolicyKeepsTheCachedBase() throws IOException {
        Path project = fixture("");
        BuildResult result = run(project);

        assertThat(result.task(":buildCustomSharedLayer").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(Files.exists(tempDir.resolve("build-count"))).isFalse();
        assertThat(result.getOutput()).contains("-H:LayerUse=" + tempDir.resolve("common/base-layer.nil"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "all"})
    @SuppressWarnings("unchecked")
    void customPolicyBuildsAJdkOnlyBaseEveryInvocation(String binarySelector) throws IOException {
        String option = "--future-defaults=run-time-initialize-file-system-providers";
        Path project = fixture(binarySelector + " { buildArgs.add('" + option + "') }");
        BuildResult first = run(project);
        BuildResult second = run(project);

        assertThat(first.task(":buildCustomSharedLayer").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(second.task(":buildCustomSharedLayer").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readAllLines(tempDir.resolve("build-count"))).hasSize(2);
        String layerArgument = second.getOutput().lines().filter(line -> line.startsWith("selectedBase="))
                .findFirst().orElseThrow().substring("selectedBase=".length());
        Path base = Path.of(layerArgument.substring("-H:LayerUse=".length()));
        assertThat(base).exists();
        assertThat(base).startsWith(project.resolve("build/native/customSharedLayer"));
        Map<String, Object> manifest = (Map<String, Object>) new JsonSlurper()
                .parse(base.resolveSibling("manifest.json").toFile());
        assertThat((List<String>) manifest.get("nativeImageArgs")).contains(option);
        List<String> command = Files.readAllLines(tempDir.resolve("last-command"));
        assertThat(command).containsOnlyOnce(option);
        assertThat(command).anyMatch(argument -> argument.startsWith("-H:LayerCreate=")
                && argument.contains("module=java.base") && !argument.contains("path="));
        assertThat(command).noneMatch(argument -> argument.contains("JUnitPlatformFeature") || argument.contains("org.junit"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void thirdPartyProviderStaysOnTheApplicationAndOutOfTheBaseManifest() throws IOException {
        String option = "-H:AdditionalSecurityProviders=org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider";
        Path project = fixture("test { buildArgs.add('" + option + "') }");
        Files.writeString(project.resolve("build.gradle"), """

                tasks.named('inspectSelectedBase') {
                    doLast { assert graalvmNative.binaries.test.buildArgs.get().contains('%s') }
                }
                """.formatted(option), StandardOpenOption.APPEND);

        BuildResult result = run(project);
        assertThat(result.task(":buildCustomSharedLayer").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readAllLines(tempDir.resolve("last-command"))).doesNotContain(option);
        String selected = result.getOutput().lines().filter(line -> line.startsWith("selectedBase="))
                .findFirst().orElseThrow().substring("selectedBase=-H:LayerUse=".length());
        Map<String, Object> manifest = (Map<String, Object>) new JsonSlurper()
                .parse(Path.of(selected).resolveSibling("manifest.json").toFile());
        assertThat((List<String>) manifest.get("nativeImageArgs")).doesNotContain(option);
    }

    @Test
    void producerAndShardReuseMatchingManifests() throws IOException {
        Path project = commonBaseFixture();

        run(project, "buildCommonFixtureLayer");
        run(project, "buildCommonFixtureLayer");
        runner(project, "buildCommonFixtureLayer", "-PrequireExisting=true").build();

        assertThat(Files.readAllLines(tempDir.resolve("build-count"))).hasSize(1);
    }

    @Test
    void shardRejectsMissingBaseWithoutBuilding() throws IOException {
        Path project = commonBaseFixture();

        BuildResult result = runner(project, "buildCommonFixtureLayer", "-PrequireExisting=true").buildAndFail();

        assertThat(result.getOutput()).contains("common-base rebuilding is disabled for this shard");
        assertThat(tempDir.resolve("build-count")).doesNotExist();
    }

    @Test
    void shardRejectsIncompatibleManifestWithoutReplacingBase() throws IOException {
        Path project = commonBaseFixture();
        run(project, "buildCommonFixtureLayer");
        Path manifest = tempDir.resolve("common/manifest.json");
        String incompatible = Files.readString(manifest).replace("current-defaults", "future-defaults");
        Files.writeString(manifest, incompatible);

        BuildResult result = runner(project, "buildCommonFixtureLayer", "-PrequireExisting=true").buildAndFail();

        assertThat(result.getOutput()).contains("common-base rebuilding is disabled for this shard");
        assertThat(Files.readString(manifest)).isEqualTo(incompatible);
        assertThat(tempDir.resolve("common/base-layer.nil")).exists();
        assertThat(Files.readAllLines(tempDir.resolve("build-count"))).hasSize(1);
    }

    private Path commonBaseFixture() throws IOException {
        Path project = fixture("");
        Path buildFile = project.resolve("build.gradle");
        Files.writeString(buildFile, """
                import org.graalvm.internal.tck.utils.SharedLayerBuildUtils
                import org.gradle.process.ExecOperations
                """ + Files.readString(buildFile));
        Files.writeString(buildFile, """

                tasks.register('buildCommonFixtureLayer') {
                    doLast {
                        SharedLayerBuildUtils.build(project, project.services.get(ExecOperations),
                            file('%s'), '%s', 'current-defaults', ['--verbose'], true,
                            project.hasProperty('requireExisting'))
                    }
                }
                """.formatted(tempDir.resolve("common/base-layer.nil"), tempDir.resolve("graal/bin/native-image")),
                StandardOpenOption.APPEND);

        return project;
    }

    private Path fixture(String binaries) throws IOException {
        Path project = LayerBuildOptionsTestFixture.create(tempDir, binaries, false);
        Files.writeString(project.resolve("build.gradle"), """

                tasks.register('inspectSelectedBase') {
                    doLast {
                        println 'selectedBase=' + graalvmNative.binaries.test.buildArgs.get()
                            .find { it.startsWith('-H:LayerUse=') }
                    }
                }
                """, StandardOpenOption.APPEND);
        Path tool = Files.createDirectories(tempDir.resolve("graal/bin")).resolve("native-image");
        Files.writeString(tool, """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                    echo 'native-image fixture'
                    exit 0
                fi
                printf 'build\\n' >> '%s'
                printf '%%s\\n' "$@" > '%s'
                for argument in "$@"; do
                    case "$argument" in
                        -H:LayerCreate=*)
                            filename=${argument#-H:LayerCreate=}
                            touch "${filename%%%%,*}"
                            ;;
                    esac
                done
                """.formatted(tempDir.resolve("build-count"), tempDir.resolve("last-command")));
        assertThat(tool.toFile().setExecutable(true)).isTrue();
        return project;
    }

    private BuildResult run(Path project) {
        return run(project, "buildCustomSharedLayer");
    }

    private BuildResult run(Path project, String buildTask) {
        return runner(project, buildTask).build();
    }

    private GradleRunner runner(Path project, String... arguments) {
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("GRAALVM_HOME", tempDir.resolve("graal").toString());
        List<String> tasks = new ArrayList<>(List.of(arguments));
        tasks.addAll(List.of("inspectSelectedBase", "--offline", "--quiet"));
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withEnvironment(environment)
                .withArguments(tasks);
    }
}
