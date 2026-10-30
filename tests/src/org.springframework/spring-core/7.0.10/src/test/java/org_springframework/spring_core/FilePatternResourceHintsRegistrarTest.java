/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.ResourceHints;
import org.springframework.aot.hint.support.FilePatternResourceHintsRegistrar;

/** Verifies file patterns are registered only beneath existing classpath directories. */
public class FilePatternResourceHintsRegistrarTest {
    @Test
    void registersPrefixedPropertyFilesAtExistingLocation() {
        ResourceHints hints = new ResourceHints();

        FilePatternResourceHintsRegistrar.forClassPathLocations("org_springframework/spring_core", "absent-config")
                .withFilePrefixes("dynamic-")
                .withFileExtensions(".properties")
                .registerHints(hints, getClass().getClassLoader());

        assertThat(hints.resourcePatternHints().flatMap(patterns -> patterns.getIncludes().stream())
                .map(pattern -> pattern.getPattern()))
                .contains("org_springframework/spring_core/dynamic-*.properties")
                .doesNotContain("absent-config/dynamic-*.properties");
    }
}
