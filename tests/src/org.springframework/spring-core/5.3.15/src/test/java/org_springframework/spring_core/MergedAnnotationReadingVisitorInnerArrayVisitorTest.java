/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

public class MergedAnnotationReadingVisitorInnerArrayVisitorTest {

    @Test
    void readsAnnotationArrayAttributesFromBytecode() throws IOException {
        AnnotationMetadata metadata = new SimpleMetadataReaderFactory(
                MergedAnnotationReadingVisitorInnerArrayVisitorTest.class.getClassLoader()
        ).getMetadataReader(new ByteArrayResource(generateAnnotatedClass())).getAnnotationMetadata();

        MergedAnnotation<ArrayAttributes> annotation = metadata.getAnnotations().get(ArrayAttributes.class);

        assertThat(annotation.isPresent()).isTrue();
        assertThat(annotation.getStringArray("names")).containsExactly("alpha", "bravo");
        assertThat(annotation.getEnumArray("modes", Mode.class)).containsExactly(Mode.FAST, Mode.SAFE);
        assertThat(annotation.getAnnotationArray("nested", NestedAttribute.class))
                .extracting(nested -> nested.getString("value"))
                .containsExactly("first", "second");
    }

    private static byte[] generateAnnotatedClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "org_springframework/spring_core/GeneratedMergedAnnotationReadingVisitorInnerArrayVisitorSample",
                null,
                Type.getInternalName(Object.class),
                null
        );

        AnnotationVisitor annotation = writer.visitAnnotation(Type.getDescriptor(ArrayAttributes.class), true);
        visitStringArray(annotation);
        visitEnumArray(annotation);
        visitNestedAnnotationArray(annotation);
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

    private static void visitEnumArray(AnnotationVisitor annotation) {
        AnnotationVisitor modes = annotation.visitArray("modes");
        modes.visitEnum(null, Type.getDescriptor(Mode.class), Mode.FAST.name());
        modes.visitEnum(null, Type.getDescriptor(Mode.class), Mode.SAFE.name());
        modes.visitEnd();
    }

    private static void visitNestedAnnotationArray(AnnotationVisitor annotation) {
        AnnotationVisitor nested = annotation.visitArray("nested");
        visitNestedAnnotation(nested, "first");
        visitNestedAnnotation(nested, "second");
        nested.visitEnd();
    }

    private static void visitNestedAnnotation(AnnotationVisitor nested, String value) {
        AnnotationVisitor nestedAnnotation = nested.visitAnnotation(null, Type.getDescriptor(NestedAttribute.class));
        nestedAnnotation.visit("value", value);
        nestedAnnotation.visitEnd();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ArrayAttributes {

        String[] names();

        Mode[] modes();

        NestedAttribute[] nested();
    }

    public enum Mode {

        FAST,

        SAFE
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAttribute {

        String value();
    }
}
