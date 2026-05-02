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
import org.springframework.util.unit.DataUnit;

public class AbstractRecursiveAnnotationVisitorTest {

    @Test
    @SuppressWarnings("deprecation")
    void resolvesEnumAnnotationAttributesFromBytecode() {
        AnnotationMetadataReadingVisitor visitor = new AnnotationMetadataReadingVisitor(
                AbstractRecursiveAnnotationVisitorTest.class.getClassLoader()
        );
        new ClassReader(generateAnnotatedClass()).accept(visitor, 0);

        AnnotationAttributes attributes = visitor.getAnnotationAttributes(DataUnitAttribute.class.getName(), false);

        assertThat(attributes).isNotNull();
        assertThat(attributes.get("unit")).isSameAs(DataUnit.KILOBYTES);
    }

    private static byte[] generateAnnotatedClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "org_springframework/spring_core/GeneratedAbstractRecursiveAnnotationVisitorSample",
                null,
                Type.getInternalName(Object.class),
                null
        );

        AnnotationVisitor annotation = writer.visitAnnotation(Type.getDescriptor(DataUnitAttribute.class), true);
        annotation.visitEnum("unit", Type.getDescriptor(DataUnit.class), DataUnit.KILOBYTES.name());
        annotation.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface DataUnitAttribute {

        DataUnit unit();
    }
}
