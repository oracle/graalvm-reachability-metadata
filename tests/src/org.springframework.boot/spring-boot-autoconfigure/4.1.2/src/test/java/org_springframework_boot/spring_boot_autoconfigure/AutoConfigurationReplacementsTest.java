/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationImportSelector;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.core.type.AnnotationMetadata;

import static org.assertj.core.api.Assertions.assertThat;

public class AutoConfigurationReplacementsTest {
    @Test
    void appliesExclusionsUsingTheOldConfigurationName() {
        AutoConfigurationImportSelector selector = AutoConfigurationTestSupport.selector(getClass().getClassLoader());
        assertThat(selector.selectImports(AnnotationMetadata.introspect(DefaultApplication.class)))
                .contains("org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration");
        assertThat(selector.selectImports(AnnotationMetadata.introspect(ExcludingApplication.class)))
                .doesNotContain("org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
                .contains("org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration");
    }

    @EnableAutoConfiguration
    public static class DefaultApplication {
    }

    @EnableAutoConfiguration(excludeName = "example.retired.JsonAutoConfiguration")
    public static class ExcludingApplication {
    }
}
