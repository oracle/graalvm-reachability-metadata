/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.annotations.common.annotationfactory.AnnotationDescriptor;
import org.hibernate.annotations.common.annotationfactory.AnnotationFactory;
import org.junit.jupiter.api.Test;

public class StandardClassLoaderDelegateImplTest {
    @Test
    public void createUsingTcclBuildsProxyWithContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader testClassLoader = StandardClassLoaderDelegateImplTest.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(testClassLoader);
        try {
            AnnotationDescriptor descriptor = descriptor("from-tccl");

            SampleAnnotation annotation = AnnotationFactory.createUsingTccl(descriptor);

            assertThat(annotation.value()).isEqualTo("from-tccl");
            assertThat(annotation.annotationType()).isEqualTo(SampleAnnotation.class);
            assertThat(annotation.getClass().getClassLoader()).isEqualTo(testClassLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void createUsesAnnotationTypeClassLoaderWithoutContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            AnnotationDescriptor descriptor = descriptor("from-annotation-type-loader");

            SampleAnnotation annotation = AnnotationFactory.create(descriptor);

            assertThat(annotation.value()).isEqualTo("from-annotation-type-loader");
            assertThat(annotation.annotationType()).isEqualTo(SampleAnnotation.class);
            assertThat(annotation.getClass().getClassLoader()).isEqualTo(SampleAnnotation.class.getClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static AnnotationDescriptor descriptor(String value) {
        AnnotationDescriptor descriptor = new AnnotationDescriptor(SampleAnnotation.class);
        descriptor.setValue("value", value);
        return descriptor;
    }

    public @interface SampleAnnotation {
        String value();
    }
}
