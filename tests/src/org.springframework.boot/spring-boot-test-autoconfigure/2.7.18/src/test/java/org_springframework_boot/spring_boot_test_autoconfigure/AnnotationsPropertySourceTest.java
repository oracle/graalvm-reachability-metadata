/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test_autoconfigure;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.autoconfigure.properties.AnnotationsPropertySource;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;
import org.springframework.boot.test.autoconfigure.properties.SkipPropertyMapping;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationsPropertySourceTest {

    @Test
    void mapsAnnotationAndNestedAnnotationAttributesToProperties() {
        AnnotationsPropertySource propertySource = new AnnotationsPropertySource(AnnotatedTestClass.class);

        assertThat(propertySource.isEmpty()).isFalse();
        assertThat(propertySource.getPropertyNames()).contains(
                "example.enabled",
                "example.service.custom-name",
                "example.service.port");
        assertThat(propertySource.getProperty("example.enabled")).isEqualTo(true);
        assertThat(propertySource.getProperty("example.service.custom-name")).isEqualTo("backend");
        assertThat(propertySource.getProperty("example.service.port")).isEqualTo(9090);
    }

    @ExampleMapping(enabled = true, service = @NestedService(customName = "backend", port = 9090))
    static class AnnotatedTestClass {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @PropertyMapping(value = "example", skip = SkipPropertyMapping.NO)
    @interface ExampleMapping {

        boolean enabled() default false;

        NestedService service() default @NestedService;

    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface NestedService {

        String customName() default "default";

        int port() default 8080;

    }

}
