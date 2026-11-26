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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Keeps the discovery baseline through matrix expansion and test alias selection.
/// §FS-library-version-update-automation.1 §AR-test-harness.7
class ReleaseLineCompatibilityTests {
    @TempDir
    Path projectDir;

    @Test
    @SuppressWarnings("unchecked")
    void matrixPreservesIndependentLinesAndTheirBaselines() throws IOException {
        fixture();
        String output = run("generateNewLibraryVersionCompatibilityMatrix",
                "-PnewLibraryVersionCandidates=" + """
                        [{"name":"io.netty:netty-common","releaseLine":"4.2","baselineVersion":"4.2.19.Final",
                          "versions":["4.2.20.Final"]},
                         {"name":"io.netty:netty-common","releaseLine":"5","baselineVersion":"5.0.0.Alpha2",
                          "versions":["5.0.0.Alpha3"]}]
                        """);
        String json = output.lines().filter(line -> line.startsWith("matrix="))
                .findFirst().orElseThrow().substring("matrix=".length());
        Map<String, Object> matrix = (Map<String, Object>) new JsonSlurper().parseText(json);
        List<Map<String, Object>> entries = (List<Map<String, Object>>) matrix.get("include");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).containsEntry("releaseLine", "4.2")
                .containsEntry("baselineVersion", "4.2.19.Final");
        assertThat(entries.get(1)).containsEntry("releaseLine", "5")
                .containsEntry("baselineVersion", "5.0.0.Alpha2");
    }

    @Test
    void exactBaselineUsesItsOwningMetadataAndTestAlias() throws IOException {
        fixture();
        String output = run("extractLibraryTestParams", "-Pcoordinates=io.netty:netty-common:4.2.19.Final");
        assertThat(output).contains("LATEST_VERSION=4.2.19.Final",
                "TEST_COORDINATES=io.netty:netty-common:4.2.1.Final",
                "TEST_PATH=io.netty/netty-common/4.1.115.Final");
        assertThat(run("extractLibraryTestParams", "-Pcoordinates=io.netty:netty-common"))
                .contains("LATEST_VERSION=5.0.0.Alpha2", "TEST_COORDINATES=io.netty:netty-common:5.0.0.Alpha2");
    }

    private void fixture() throws IOException {
        Files.createDirectories(projectDir.resolve("tests/tck-build-logic"));
        Files.writeString(projectDir.resolve("LICENSE"), "test");
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'release-lines'\n");
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'base'
                    id 'org.graalvm.internal.tck-harness'
                }
                """);
        Files.writeString(projectDir.resolve("ci.json"), """
                {"nativeImageModes":{"current-defaults":[]},
                 "generateNewLibraryVersionCompatibilityMatrix":{
                   "java":["25"],"os":["ubuntu-latest"],"native-image-modes":["current-defaults"],
                   "versions-per-job":30,"max-libraries":10}}
                """);
        Path index = projectDir.resolve("metadata/io.netty/netty-common/index.json");
        Files.createDirectories(index.getParent());
        Files.writeString(index, """
                [{"latest":true,"metadata-version":"5.0.0.Alpha2","tested-versions":["5.0.0.Alpha2"]},
                 {"metadata-version":"4.2.1.Final","test-version":"4.1.115.Final",
                  "tested-versions":["4.2.1.Final","4.2.19.Final"]}]
                """);
    }

    private String run(String... arguments) {
        return GradleRunner.create().withProjectDir(projectDir.toFile()).withPluginClasspath()
                .withArguments(arguments).build().getOutput();
    }
}
