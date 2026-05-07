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

public class AnnotationProxyTest {
    @Test
    public void createdAnnotationExposesConfiguredDefaultAndAnnotationTypeValues() {
        AnnotationDescriptor descriptor = new AnnotationDescriptor(SampleAnnotation.class);
        descriptor.setValue("name", "created-by-descriptor");
        descriptor.setValue("priority", 7);

        SampleAnnotation annotation = AnnotationFactory.create(descriptor);

        assertThat(annotation.name()).isEqualTo("created-by-descriptor");
        assertThat(annotation.priority()).isEqualTo(7);
        assertThat(annotation.category()).isEqualTo("default-category");
        assertThat(annotation.annotationType()).isEqualTo(SampleAnnotation.class);
    }

    public @interface SampleAnnotation {
        String name();

        int priority();

        String category() default "default-category";
    }
}
