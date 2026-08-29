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

import org.junit.jupiter.api.Test;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.Type;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor;
import org.springframework.lang.NonNullApi;

/** Verifies legacy bytecode reading of annotation array attributes. */
@SuppressWarnings("deprecation")
public class RecursiveAnnotationArrayVisitorTest {
    @Test
    void readsEnumArrayFromLibraryClass() throws Exception {
        AnnotationMetadataReadingVisitor visitor = readMetadata(NonNullApi.class);
        AnnotationAttributes attributes = visitor.getAnnotationAttributes(
                "javax.annotation.meta.TypeQualifierDefault", false);

        assertThat(attributes).isNotNull();
        assertThat((ElementType[]) attributes.get("value"))
                .containsExactly(ElementType.METHOD, ElementType.PARAMETER);
    }

    @Test
    void visitsExplicitlyEmptyArrayUsingAnnotationReturnType() {
        AnnotationMetadataReadingVisitor visitor =
                new AnnotationMetadataReadingVisitor(getClass().getClassLoader());
        AnnotationVisitor annotation =
                visitor.visitAnnotation(Type.getDescriptor(EmptyArrayAnnotation.class), true);
        AnnotationVisitor array = annotation.visitArray("value");

        array.visitEnd();
        annotation.visitEnd();
        AnnotationAttributes attributes =
                visitor.getAnnotationAttributes(EmptyArrayAnnotation.class.getName(), false);

        assertThat(attributes).isNotNull();
        assertThat((String[]) attributes.get("value")).isEmpty();
    }

    private static AnnotationMetadataReadingVisitor readMetadata(Class<?> type) throws Exception {
        String path = type.getName().replace('.', '/') + ".class";
        AnnotationMetadataReadingVisitor visitor =
                new AnnotationMetadataReadingVisitor(type.getClassLoader());
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            new ClassReader(input).accept(visitor, ClassReader.SKIP_CODE);
        }
        return visitor;
    }

    public @interface EmptyArrayAnnotation {
        String[] value();
    }
}
