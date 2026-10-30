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

/** Verifies resource hints are conditional on a real classpath location. */
public class ResourceHintsTest {
    @Test
    void contributesPatternsOnlyForPresentResources() {
        ResourceHints hints = new ResourceHints();
        ClassLoader loader = getClass().getClassLoader();

        hints.registerPatternIfPresent(loader, "org_springframework/spring_core/dynamic-access.properties",
                builder -> builder.includes("config/*.properties"));
        hints.registerPatternIfPresent(loader, "absent-spring-config.properties",
                builder -> builder.includes("absent/*.properties"));

        assertThat(hints.resourcePatternHints().flatMap(patterns -> patterns.getIncludes().stream())
                .map(pattern -> pattern.getPattern()))
                .contains("config/*.properties").doesNotContain("absent/*.properties");
    }
}
