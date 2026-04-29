/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.AnnotationDescriptor;
import org.hibernate.models.spi.AttributeDescriptor;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractJdkValueExtractorTest {
    @Test
    public void extractsAttributeValueFromJdkAnnotationUsage() {
        final ModelsContext context = newModelsContext();
        final ExtractedLabel label = AnnotationAccess.getAnnotation(AnnotatedEntity.class, ExtractedLabel.class);
        final AnnotationDescriptor<ExtractedLabel> descriptor = context.getAnnotationDescriptorRegistry()
                .getDescriptor(ExtractedLabel.class);
        final AttributeDescriptor<String> valueAttribute = descriptor.getAttribute("value");

        final String extractedValue = valueAttribute.getTypeDescriptor()
                .createJdkValueExtractor(context)
                .extractValue(label, valueAttribute, context);

        assertThat(extractedValue).isEqualTo("runtime-label");
    }

    @Test
    public void extractsNamedAttributeValueThroughAnnotationDescriptorLookup() {
        final ModelsContext context = newModelsContext();
        final ExtractedLabel label = AnnotationAccess.getAnnotation(AnnotatedEntity.class, ExtractedLabel.class);
        final AnnotationDescriptor<ExtractedLabel> descriptor = context.getAnnotationDescriptorRegistry()
                .getDescriptor(ExtractedLabel.class);
        final AttributeDescriptor<Integer> priorityAttribute = descriptor.getAttribute("priority");

        final Integer extractedPriority = priorityAttribute.getTypeDescriptor()
                .createJdkValueExtractor(context)
                .extractValue(label, "priority", context);

        assertThat(extractedPriority).isEqualTo(11);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    private static final class AnnotationAccess {
        private AnnotationAccess() {
        }

        private static <A extends Annotation> A getAnnotation(AnnotatedElement element, Class<A> annotationType) {
            try {
                return annotationType.cast(AnnotatedElement.class.getMethod("getAnnotation", Class.class)
                        .invoke(element, annotationType));
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    @ExtractedLabel(value = "runtime-label", priority = 11)
    private static final class AnnotatedEntity {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ExtractedLabel {
        String value();

        int priority() default 0;
    }
}
