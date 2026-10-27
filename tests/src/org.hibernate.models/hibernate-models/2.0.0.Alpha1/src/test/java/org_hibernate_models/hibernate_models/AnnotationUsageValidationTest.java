/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.AnnotationDescriptor;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class AnnotationUsageValidationTest {
    @Test
    @SuppressWarnings("annotationAccess")
    public void validatesValuesObtainedFromPublicAnnotationUsage() {
        final ModelsContext context = newModelsContext();
        final AnnotationDescriptor<ValidatedLabel> descriptor = context.getAnnotationDescriptorRegistry()
                .getDescriptor(ValidatedLabel.class);
        final ValidatedLabel usage = AnnotatedEntity.class.getAnnotation(ValidatedLabel.class);

        assertThat(usage).isNotNull();
        assertThatCode(() -> descriptor.validateUsage(usage, context))
                .doesNotThrowAnyException();
        assertThat(usage.value()).isEqualTo("validated");
        assertThat(usage.priority()).isEqualTo(13);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidatedLabel {
        String value();

        int priority();
    }

    @ValidatedLabel(value = "validated", priority = 13)
    private static final class AnnotatedEntity {
        private AnnotatedEntity() {
        }
    }
}
