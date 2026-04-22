/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_classfilewriter.jboss_classfilewriter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassField;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.junit.jupiter.api.Test;

public class AnnotationBuilderTest {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface TypeLabel {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface FieldOrder {
        int value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ReturnTypeHint {
        Class<?> value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface OptionalFlag {
        boolean value();
    }

    @TypeLabel("generated")
    private static final class ClassAnnotationSource {
    }

    private static final class MemberAnnotationSource {
        @FieldOrder(7)
        public int order;

        @ReturnTypeHint(String.class)
        public String transform(@OptionalFlag(true) String value) {
            return value;
        }
    }

    @Test
    void copiesRuntimeVisibleAnnotationsFromJavaAnnotationInstances() throws Exception {
        TestClassLoader targetClassLoader = new TestClassLoader(getClass().getClassLoader());
        String className = generatedClassName("GeneratedAnnotationBuilderCoverage");
        ClassFile classFile = new ClassFile(
                className,
                AccessFlag.of(AccessFlag.SUPER, AccessFlag.PUBLIC),
                Object.class.getName(),
                targetClassLoader
        );

        classFile.getRuntimeVisibleAnnotationsAttribute()
                .addAnnotation(ClassAnnotationSource.class.getAnnotation(TypeLabel.class));

        Field sourceField = MemberAnnotationSource.class.getDeclaredField("order");
        ClassField generatedField = classFile.addField(AccessFlag.PUBLIC, "order", int.class);
        generatedField.getRuntimeVisibleAnnotationsAttribute()
                .addAnnotation(sourceField.getAnnotation(FieldOrder.class));

        Method sourceMethod = MemberAnnotationSource.class.getDeclaredMethod("transform", String.class);
        ClassMethod generatedMethod = classFile.addMethod(AccessFlag.PUBLIC, "transform", "Ljava/lang/String;", "Ljava/lang/String;");
        generatedMethod.getRuntimeVisibleAnnotationsAttribute()
                .addAnnotation(sourceMethod.getAnnotation(ReturnTypeHint.class));
        generatedMethod.getRuntimeVisibleParameterAnnotationsAttribute()
                .addAnnotation(0, sourceMethod.getParameters()[0].getAnnotation(OptionalFlag.class));
        CodeAttribute code = generatedMethod.getCodeAttribute();
        code.aload(1);
        code.returnInstruction();

        byte[] bytecode = classFile.toBytecode();
        Class<?> generatedClass = targetClassLoader.defineGeneratedClass(className, bytecode, 0, bytecode.length);

        TypeLabel classAnnotation = generatedClass.getAnnotation(TypeLabel.class);
        FieldOrder fieldAnnotation = generatedClass.getDeclaredField("order").getAnnotation(FieldOrder.class);
        Method generatedTransformMethod = generatedClass.getDeclaredMethod("transform", String.class);
        ReturnTypeHint methodAnnotation = generatedTransformMethod.getAnnotation(ReturnTypeHint.class);
        OptionalFlag parameterAnnotation = generatedTransformMethod.getParameters()[0].getAnnotation(OptionalFlag.class);

        assertThat(classAnnotation).isNotNull();
        assertThat(classAnnotation.value()).isEqualTo("generated");
        assertThat(fieldAnnotation).isNotNull();
        assertThat(fieldAnnotation.value()).isEqualTo(7);
        assertThat(methodAnnotation).isNotNull();
        assertThat(methodAnnotation.value()).isEqualTo(String.class);
        assertThat(parameterAnnotation).isNotNull();
        assertThat(parameterAnnotation.value()).isTrue();
    }

    private static String generatedClassName(String simpleName) {
        return AnnotationBuilderTest.class.getPackageName() + "." + simpleName + System.nanoTime();
    }

    public static class TestClassLoader extends ClassLoader {
        public TestClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineGeneratedClass(String name, byte[] bytecode, int offset, int length) {
            return defineClass(name, bytecode, offset, length);
        }
    }
}
