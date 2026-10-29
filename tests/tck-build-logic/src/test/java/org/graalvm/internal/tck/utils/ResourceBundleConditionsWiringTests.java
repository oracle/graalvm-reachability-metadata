/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// The two halves of the GR-80923 restoration sit around native-build-tools' merge and cleanup.
class ResourceBundleConditionsWiringTests {

    @TempDir
    Path projectDir;

    @Test
    void keepPrecedesTheAgentMergeAndCleanupWhichPrecedeRestore() throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'wiring'\n");
        Files.writeString(projectDir.resolve("build.gradle"), """
                import org.graalvm.internal.tck.utils.ResourceBundleConditions

                plugins {
                    id 'java'
                    id 'org.graalvm.buildtools.native'
                }

                ResourceBundleConditions.install(project, "native-image-utils")

                graalvmNative.agent.defaultMode = "conditional"
                graalvmNative.agent.modes.conditional.userCodeFilterPath = "user-code-filter.json"

                tasks.register("testActions") {
                    doLast {
                        tasks.test.actions.each { wrapper ->
                            String name = wrapper.displayName
                            try { name += " " + wrapper.@action.class.simpleName } catch (Throwable ignored) { }
                            println "action: " + name
                        }
                    }
                }
                """);

        List<String> actions = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("testActions", "-Pagent", "--quiet")
                .build()
                .getOutput()
                .lines()
                .filter(line -> line.startsWith("action: "))
                .toList();

        assertThat(actions).filteredOn(action -> action.contains("GR-80923")
                        || action.contains("MergeAgentFilesAction") || action.contains("CleanupAgentFilesAction"))
                .hasSize(4)
                .satisfiesExactly(
                        keep -> assertThat(keep).contains("GR-80923: keep the agent's call trees"),
                        merge -> assertThat(merge).contains("MergeAgentFilesAction"),
                        cleanup -> assertThat(cleanup).contains("CleanupAgentFilesAction"),
                        restore -> assertThat(restore).contains("GR-80923: restore resource bundle conditions"));
    }
}
