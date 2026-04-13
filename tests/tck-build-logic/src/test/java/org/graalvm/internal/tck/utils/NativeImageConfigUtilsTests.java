/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeImageConfigUtilsTests {

    @Test
    void resolveSelectedModePrefersEnvironmentOverride() {
        assertThat(NativeImageConfigUtils.resolveSelectedMode("future-defaults-all", "current-defaults"))
                .isEqualTo("future-defaults-all");
    }

    @Test
    void resolveSelectedModeFallsBackToDefault() {
        assertThat(NativeImageConfigUtils.resolveSelectedMode(null, null))
                .isEqualTo(NativeImageConfigUtils.DEFAULT_MODE);
    }

    @Test
    void resolvedBuildArgsMergesBaseAndModeArgsAndExpandsPlaceholders() {
        Map<String, Object> ci = new LinkedHashMap<>();
        ci.put("buildArgs", List.of("--verbose", "--library={{library.version}}"));
        ci.put("nativeImageModes", new LinkedHashMap<>(Map.of(
                "current-defaults", List.of(),
                "future-defaults-all", List.of("--future-defaults=all", "--for={{library.coordinates}}")
        )));

        assertThat(NativeImageConfigUtils.resolvedBuildArgs(
                ci,
                "future-defaults-all",
                Map.of(
                        "{{library.version}}", "1.2.3",
                        "{{library.coordinates}}", "g:a:1.2.3"
                )
        )).containsExactly(
                "--verbose",
                "--library=1.2.3",
                "--future-defaults=all",
                "--for=g:a:1.2.3"
        );
    }

    @Test
    void resolvedBuildArgsRejectsUnknownMode() {
        Map<String, Object> ci = new LinkedHashMap<>();
        ci.put("buildArgs", List.of("--verbose"));
        ci.put("nativeImageModes", new LinkedHashMap<>(Map.of(
                "current-defaults", List.of(),
                "future-defaults-all", List.of("--future-defaults=all")
        )));

        assertThatThrownBy(() -> NativeImageConfigUtils.resolvedBuildArgs(ci, "missing-mode", Map.of()))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("Unknown native-image mode 'missing-mode'");
    }

    @Test
    void modeNamesPreserveCiOrder() {
        Map<String, Object> ci = new LinkedHashMap<>();
        ci.put("buildArgs", List.of("--verbose"));
        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put("current-defaults", List.of());
        modes.put("future-defaults-all", List.of("--future-defaults=all"));
        ci.put("nativeImageModes", modes);

        assertThat(NativeImageConfigUtils.modeNames(ci))
                .containsExactly("current-defaults", "future-defaults-all");
    }

    @Test
    void javaVersionsByModeUsesOverridesWhenPresent() {
        Map<String, Object> ci = new LinkedHashMap<>();
        ci.put("buildArgs", List.of("--verbose"));
        Map<String, Object> nativeImageModes = new LinkedHashMap<>();
        nativeImageModes.put("current-defaults", List.of());
        nativeImageModes.put("future-defaults-all", List.of("--future-defaults=all"));
        ci.put("nativeImageModes", nativeImageModes);
        ci.put("nativeImageModeJavaVersions", Map.of(
                "future-defaults-all", List.of("latest-ea")
        ));

        assertThat(NativeImageConfigUtils.javaVersionsByMode(
                ci,
                List.of("25", "latest-ea"),
                List.of("current-defaults", "future-defaults-all")
        )).containsExactly(
                Map.entry("current-defaults", List.of("25", "latest-ea")),
                Map.entry("future-defaults-all", List.of("latest-ea"))
        );
    }

    @Test
    void expandMatrixEntriesAddsNativeImageModeToEveryEnvironmentCombination() {
        List<Map<String, Object>> include = NativeImageConfigUtils.expandMatrixEntries(
                List.of(Map.of("coordinates", "1/2")),
                List.of("25"),
                List.of("ubuntu-latest", "macos-latest"),
                List.of("current-defaults", "future-defaults-all"),
                Map.of(
                        "current-defaults", List.of("25"),
                        "future-defaults-all", List.of("25")
                )
        );

        assertThat(include).containsExactly(
                Map.of(
                        "coordinates", "1/2",
                        "version", "25",
                        "os", "ubuntu-latest",
                        "nativeImageMode", "current-defaults"
                ),
                Map.of(
                        "coordinates", "1/2",
                        "version", "25",
                        "os", "ubuntu-latest",
                        "nativeImageMode", "future-defaults-all"
                ),
                Map.of(
                        "coordinates", "1/2",
                        "version", "25",
                        "os", "macos-latest",
                        "nativeImageMode", "current-defaults"
                ),
                Map.of(
                        "coordinates", "1/2",
                        "version", "25",
                        "os", "macos-latest",
                        "nativeImageMode", "future-defaults-all"
                )
        );
    }

    @Test
    void expandMatrixEntriesSkipsJavaVersionsDisallowedForNativeImageMode() {
        List<Map<String, Object>> include = NativeImageConfigUtils.expandMatrixEntries(
                List.of(Map.of("coordinates", "1/2")),
                List.of("25", "latest-ea"),
                List.of("ubuntu-latest"),
                List.of("current-defaults", "future-defaults-all"),
                Map.of(
                        "current-defaults", List.of("25", "latest-ea"),
                        "future-defaults-all", List.of("latest-ea")
                )
        );

        assertThat(include).containsExactly(
                Map.of(
                        "coordinates", "1/2",
                        "version", "25",
                        "os", "ubuntu-latest",
                        "nativeImageMode", "current-defaults"
                ),
                Map.of(
                        "coordinates", "1/2",
                        "version", "latest-ea",
                        "os", "ubuntu-latest",
                        "nativeImageMode", "current-defaults"
                ),
                Map.of(
                        "coordinates", "1/2",
                        "version", "latest-ea",
                        "os", "ubuntu-latest",
                        "nativeImageMode", "future-defaults-all"
                )
        );
    }
}
