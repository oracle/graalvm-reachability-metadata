/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test;

import static java.lang.annotation.ElementType.TYPE;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.PropertyMapping;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestContextManager;

public class AnnotationsPropertySourceTest {
    @Test
    void mapsSimpleAndNestedAnnotationAttributesToProperties() {
        TestContextManager manager = new TestContextManager(PropertyFixture.class);
        try (ConfigurableApplicationContext context =
                (ConfigurableApplicationContext) manager.getTestContext().getApplicationContext()) {
            assertThat(context.getEnvironment().getProperty("sample.name")).isEqualTo("spring");
            assertThat(context.getEnvironment().getProperty("sample.nested.value"))
                    .isEqualTo("boot");
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(TYPE)
    @PropertyMapping("sample")
    public @interface SampleProperties {
        String name();

        NestedProperties nested();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedProperties {
        String value();
    }

    @SpringBootTest(classes = TestConfiguration.class)
    @SampleProperties(name = "spring", nested = @NestedProperties("boot"))
    static class PropertyFixture {}

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {}
}
