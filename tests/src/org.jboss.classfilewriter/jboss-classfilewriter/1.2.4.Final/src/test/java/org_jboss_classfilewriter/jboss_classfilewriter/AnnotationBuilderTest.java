/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_classfilewriter.jboss_classfilewriter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFactory;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.JavaVersions;
import org.junit.jupiter.api.Test;

public class AnnotationBuilderTest {
    private static final AtomicInteger GENERATED_CLASS_COUNTER = new AtomicInteger();
    private static final ClassFactory LOOKUP_CLASS_FACTORY = new SamePackageLookupClassFactory(AnnotationBuilderTest.class);

    @Test
    void copiesMethodAndParameterAnnotationsWhenAddingAMethod() throws Exception {
        final Method sourceMethod = AnnotationSource.class.getDeclaredMethod("annotatedMethod", String.class);
        final ClassFile classFile = new ClassFile(
            generatedClassName(),
            AccessFlag.of(AccessFlag.PUBLIC, AccessFlag.SUPER),
            Object.class.getName(),
            JavaVersions.JAVA_7,
            AnnotationBuilderTest.class.getClassLoader(),
            LOOKUP_CLASS_FACTORY
        );

        classFile.addMethod(sourceMethod).getCodeAttribute().returnInstruction();

        final Method generatedMethod = classFile.define().getDeclaredMethod("annotatedMethod", String.class);
        final SampleAnnotation methodAnnotation = generatedMethod.getDeclaredAnnotation(SampleAnnotation.class);
        final SampleAnnotation parameterAnnotation = (SampleAnnotation) generatedMethod.getParameterAnnotations()[0][0];

        assertThat(methodAnnotation).isNotNull();
        assertThat(methodAnnotation.text()).isEqualTo("method");
        assertThat(methodAnnotation.number()).isEqualTo(7);
        assertThat(parameterAnnotation.text()).isEqualTo("parameter");
        assertThat(parameterAnnotation.number()).isEqualTo(9);
    }

    private static String generatedClassName() {
        return AnnotationBuilderTest.class.getPackageName()
            + ".AnnotationBuilderGenerated"
            + GENERATED_CLASS_COUNTER.incrementAndGet();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface SampleAnnotation {
        String text();

        int number();
    }

    public static final class AnnotationSource {
        @SampleAnnotation(text = "method", number = 7)
        public void annotatedMethod(@SampleAnnotation(text = "parameter", number = 9) final String value) {
        }
    }

    private static final class SamePackageLookupClassFactory implements ClassFactory {
        private final MethodHandles.Lookup lookup;

        private SamePackageLookupClassFactory(final Class<?> anchorClass) {
            try {
                this.lookup = MethodHandles.privateLookupIn(anchorClass, MethodHandles.lookup());
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Class<?> defineClass(
            final ClassLoader loader,
            final String name,
            final byte[] bytecode,
            final int off,
            final int len,
            final ProtectionDomain protectionDomain
        ) {
            final byte[] classBytes = off == 0 && len == bytecode.length
                ? bytecode
                : Arrays.copyOfRange(bytecode, off, off + len);
            try {
                return lookup.defineClass(classBytes);
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
