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
    public void createUsingTcclDefinesAnnotationProxyWithContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader testClassLoader = StandardClassLoaderDelegateImplTest.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(testClassLoader);
        try {
            AnnotationProxyTest.SampleAnnotation annotation = AnnotationFactory.createUsingTccl(descriptor("tccl"));

            assertThat(annotation.name()).isEqualTo("tccl");
            assertThat(annotation.priority()).isEqualTo(1);
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
            AnnotationProxyTest.SampleAnnotation annotation = AnnotationFactory.create(descriptor("annotation-type"));

            assertThat(annotation.name()).isEqualTo("annotation-type");
            assertThat(annotation.priority()).isEqualTo(1);
            assertThat(annotation.annotationType()).isEqualTo(AnnotationProxyTest.SampleAnnotation.class);
            assertThat(annotation.getClass().getClassLoader())
                    .isEqualTo(AnnotationProxyTest.SampleAnnotation.class.getClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static AnnotationDescriptor descriptor(String value) {
        AnnotationDescriptor descriptor = new AnnotationDescriptor(AnnotationProxyTest.SampleAnnotation.class);
        descriptor.setValue("name", value);
        descriptor.setValue("priority", 1);
        return descriptor;
    }
}
