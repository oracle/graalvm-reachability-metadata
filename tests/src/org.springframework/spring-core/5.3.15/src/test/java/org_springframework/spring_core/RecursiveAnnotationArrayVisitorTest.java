/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor;

public class RecursiveAnnotationArrayVisitorTest {

    @Test
    void readsExplicitStringArraysAndEmptyArraysFromAnnotationBytecode() {
        AnnotationAttributes attributes = readGeneratedAnnotationAttributes();

        assertThat(attributes.getStringArray("names")).containsExactly("alpha", "bravo");
        assertThat(attributes.getStringArray("emptyNames")).isEmpty();
        assertThat(attributes.getAnnotationArray("emptyNested")).isEmpty();
    }

    private static AnnotationAttributes readGeneratedAnnotationAttributes() {
        AnnotationMetadataReadingVisitor visitor = new AnnotationMetadataReadingVisitor(
                RecursiveAnnotationArrayVisitorTest.class.getClassLoader()
        );
        new ClassReader(generateAnnotatedClass()).accept(visitor, 0);

        return visitor.getAnnotationAttributes(ArrayAttributes.class.getName(), false);
    }

    private static byte[] generateAnnotatedClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "org_springframework/spring_core/GeneratedRecursiveAnnotationArrayVisitorSample",
                null,
                Type.getInternalName(Object.class),
                null
        );

        AnnotationVisitor annotation = writer.visitAnnotation(Type.getDescriptor(ArrayAttributes.class), true);
        visitStringArray(annotation);
        visitEmptyArray(annotation, "emptyNames");
        visitEmptyArray(annotation, "emptyNested");
        annotation.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void visitStringArray(AnnotationVisitor annotation) {
        AnnotationVisitor names = annotation.visitArray("names");
        names.visit(null, "alpha");
        names.visit(null, "bravo");
        names.visitEnd();
    }

    private static void visitEmptyArray(AnnotationVisitor annotation, String attributeName) {
        AnnotationVisitor array = annotation.visitArray(attributeName);
        array.visitEnd();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ArrayAttributes {

        String[] names() default {};

        String[] emptyNames() default {};

        NestedAttribute[] emptyNested() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAttribute {

        String value() default "";
    }
}
