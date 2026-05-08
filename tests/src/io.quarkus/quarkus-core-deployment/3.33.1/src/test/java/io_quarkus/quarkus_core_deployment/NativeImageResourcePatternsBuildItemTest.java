/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core_deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;

public class NativeImageResourcePatternsBuildItemTest {
    @Test
    void builderConvertsIncludeGlobsToNativeImageResourcePatterns() {
        NativeImageResourcePatternsBuildItem resourcePatterns = NativeImageResourcePatternsBuildItem.builder()
                .includeGlob("META-INF/services/*")
                .includeGlobs(List.of("config/*.properties", "templates/*/index.html"))
                .includeGlobs("public/**")
                .build();

        List<String> includePatterns = resourcePatterns.getIncludePatterns();

        assertThat(includePatterns).hasSize(4);
        assertThat(includePatterns).anySatisfy(pattern -> assertThat(matches(pattern, "META-INF/services/acme.Service"))
                .isTrue());
        assertThat(includePatterns).anySatisfy(pattern -> assertThat(matches(pattern, "config/application.properties"))
                .isTrue());
        assertThat(includePatterns).anySatisfy(pattern -> assertThat(matches(pattern, "templates/main/index.html"))
                .isTrue());
        assertThat(includePatterns).anySatisfy(pattern -> assertThat(matches(pattern, "public/assets/logo.svg"))
                .isTrue());
        assertThat(includePatterns).noneSatisfy(pattern -> assertThat(matches(
                pattern, "META-INF/services/nested/acme.Service")).isTrue());
        assertThatThrownBy(() -> includePatterns.add("extra-resource"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyBuilderCreatesEmptyImmutablePatternLists() {
        NativeImageResourcePatternsBuildItem resourcePatterns = NativeImageResourcePatternsBuildItem.builder().build();

        assertThat(resourcePatterns.getIncludePatterns()).isEmpty();
        assertThatThrownBy(() -> resourcePatterns.getIncludePatterns().add("application.properties"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static boolean matches(String pattern, String resource) {
        return Pattern.compile(pattern).matcher(resource).matches();
    }
}
