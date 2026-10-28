/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LayeredBuildArgsUtilsTests {
    @Test
    void excludesApplicationFixturePathsButRetainsJdkModuleRequests() {
        List<String> testArguments = List.of(
                "--module-path", "/test/module.jar", "--class-path=/test/classes",
                "--add-modules", "java.xml,com.example.testmodule",
                "--add-modules=java.management", "-H:+AddAllCharsets");

        assertThat(LayeredBuildArgsUtils.dedicatedLayerArguments(
                testArguments, List.of(), List.of())).containsExactly(
                        "--add-modules", "java.xml", "--add-modules=java.management", "-H:+AddAllCharsets");
        assertThat(testArguments).contains("--module-path", "/test/module.jar");
    }

    @Test
    void sharesLibraryPolicyWithoutTestInstrumentationOrMetadata() {
        List<String> testHarness = List.of("--pgo-instrument", "-H:ConfigurationFileDirectories=/test/config");
        List<String> testArguments = List.of(
                "-Ob", "--pgo-instrument", "-H:ConfigurationFileDirectories=/test/config",
                "--enable-url-protocols=https", "-H:+AddAllCharsets",
                "--initialize-at-build-time=org.slf4j.helpers.NOPLoggerFactory",
                "-H:+AllowDeprecatedBuilderClassesOnImageClasspath");
        List<String> baseHarness = List.of(
                "-H:ConfigurationFileDirectories=/base/config", "-Djunit.selectors=/base/selectors");

        assertThat(LayeredBuildArgsUtils.dedicatedLayerArguments(
                testArguments, testHarness, baseHarness)).containsExactly(
                        "-Ob", "--enable-url-protocols=https", "-H:+AddAllCharsets",
                        "--initialize-at-build-time=org.slf4j.helpers.NOPLoggerFactory",
                        "-H:+AllowDeprecatedBuilderClassesOnImageClasspath",
                        "-H:ConfigurationFileDirectories=/base/config", "-Djunit.selectors=/base/selectors");
        assertThat(testArguments).hasSize(7);
    }

    @Test
    void preservesRepeatedLibraryOptionScopesWithoutDuplicatingCommonPolicy() {
        List<String> testHarness = List.of(
                "-H:+UnlockExperimentalVMOptions", "-H:+MetadataTracingSupport", "-H:-UnlockExperimentalVMOptions");
        List<String> testArguments = List.of(
                "-Ob", "-H:+UnlockExperimentalVMOptions", "-H:+MetadataTracingSupport", "-H:-UnlockExperimentalVMOptions",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "-H:+UnlockExperimentalVMOptions", "-H:+AddAllCharsets", "-H:-UnlockExperimentalVMOptions",
                "-H:+UnlockExperimentalVMOptions", "-H:+AllowJRTFileSystem", "-H:-UnlockExperimentalVMOptions");

        assertThat(LayeredBuildArgsUtils.dedicatedLayerArguments(
                testArguments, testHarness, List.of())).containsExactly(
                        "-Ob", "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                        "-H:+UnlockExperimentalVMOptions", "-H:+AddAllCharsets", "-H:-UnlockExperimentalVMOptions",
                        "-H:+UnlockExperimentalVMOptions", "-H:+AllowJRTFileSystem", "-H:-UnlockExperimentalVMOptions");
    }
}
