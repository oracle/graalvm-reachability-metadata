/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/** Verifies metadata reading of populated and empty annotation arrays. */
public class RecursiveAnnotationArrayVisitorTest {
    @Test
    void readsPopulatedClassArrayFromDependencyClass() throws Exception {
        MetadataReader reader = new SimpleMetadataReaderFactory(getClass().getClassLoader())
                .getMetadataReader(DisabledOnOs.class.getName());

        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                reader.getAnnotationMetadata().getAnnotationAttributes(ExtendWith.class.getName(), false));

        assertThat(attributes).isNotNull();
        assertThat((Object[]) attributes.get("value")).hasSize(1);
    }

    @Test
    void readsExplicitEmptyArraysFromAnnotation() throws Exception {
        MetadataReader reader = new SimpleMetadataReaderFactory(getClass().getClassLoader())
                .getMetadataReader(EmptyArraysFixture.class.getName());

        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                reader.getAnnotationMetadata().getAnnotationAttributes(EmptyArrays.class.getName(), false));

        assertThat(attributes).isNotNull();
        assertThat(attributes.getStringArray("names")).isEmpty();
        assertThat(attributes.getAnnotationArray("nested")).isEmpty();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface EmptyArrays {
        String[] names();

        Nested[] nested();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Nested {
        String value() default "";
    }

    @EmptyArrays(names = {}, nested = {})
    private static final class EmptyArraysFixture {
    }
}
