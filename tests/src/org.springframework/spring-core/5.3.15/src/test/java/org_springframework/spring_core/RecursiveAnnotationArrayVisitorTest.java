/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.asm.ClassReader;
import org.springframework.asm.Opcodes;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor;

/** Verifies legacy recursive reading of a populated annotation array. */
@SuppressWarnings("deprecation")
public class RecursiveAnnotationArrayVisitorTest {
    @Test
    void readsPopulatedClassArrayFromDependencyClass() throws Exception {
        AnnotationMetadataReadingVisitor visitor =
                new AnnotationMetadataReadingVisitor(getClass().getClassLoader());
        new ClassReader(DisabledOnOs.class.getName()).accept(visitor, ClassReader.SKIP_CODE);

        AnnotationAttributes attributes =
                visitor.getAnnotationAttributes(ExtendWith.class.getName(), false);

        assertThat(attributes).isNotNull();
        assertThat((Object[]) attributes.get("value")).hasSize(1);
    }

    @Test
    void readsExplicitEmptyArraysFromAnnotation() throws Exception {
        AnnotationMetadataReadingVisitor visitor =
                new AnnotationMetadataReadingVisitor(getClass().getClassLoader());
        byte[] classBytes = readFixtureClass();
        new ClassReader(classBytes).accept(visitor, ClassReader.SKIP_CODE);

        AnnotationAttributes attributes =
                visitor.getAnnotationAttributes(EmptyArrays.class.getName(), false);

        assertThat(attributes).isNotNull();
        assertThat(attributes.getStringArray("names")).isEmpty();
        assertThat(attributes.getAnnotationArray("nested")).isEmpty();
    }

    private static byte[] readFixtureClass() throws Exception {
        String resourcePath = EmptyArraysFixture.class.getName().replace('.', '/') + ".class";
        byte[] classBytes;
        try (InputStream input = new ClassPathResource(resourcePath).getInputStream()) {
            classBytes = input.readAllBytes();
        }
        classBytes[6] = 0;
        classBytes[7] = (byte) Opcodes.V1_8;
        return classBytes;
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
