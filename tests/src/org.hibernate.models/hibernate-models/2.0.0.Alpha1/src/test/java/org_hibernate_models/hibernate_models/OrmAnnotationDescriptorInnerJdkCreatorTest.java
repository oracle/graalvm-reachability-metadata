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
import org.hibernate.models.internal.OrmAnnotationDescriptor;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrmAnnotationDescriptorInnerJdkCreatorTest {
    @Test
    public void createsMutableAnnotationUsageFromJdkAnnotation() {
        final JdkLabel jdkAnnotation = AnnotationAccess.getAnnotation(AnnotatedEntity.class, JdkLabel.class);
        final ModelsContext context = newModelsContext();
        final OrmAnnotationDescriptor<JdkLabel, JdkLabelUsage> descriptor = new OrmAnnotationDescriptor<>(
                JdkLabel.class,
                JdkLabelUsage.class
        );

        final JdkLabel usage = descriptor.createUsage(jdkAnnotation, context);

        assertThat(usage).isInstanceOf(JdkLabelUsage.class);
        assertThat(usage.value()).isEqualTo("entity-name");
        assertThat(usage.priority()).isEqualTo(7);
        assertThat(usage.annotationType()).isEqualTo(JdkLabel.class);
        assertThat(((JdkLabelUsage) usage).jdkAnnotation()).isSameAs(jdkAnnotation);
        assertThat(((JdkLabelUsage) usage).context()).isSameAs(context);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    @JdkLabel(value = "entity-name", priority = 7)
    private static final class AnnotatedEntity {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface JdkLabel {
        String value();

        int priority() default 0;
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

    public static final class JdkLabelUsage implements JdkLabel {
        private final JdkLabel jdkAnnotation;
        private final ModelsContext context;

        public JdkLabelUsage(JdkLabel jdkAnnotation, ModelsContext context) {
            this.jdkAnnotation = jdkAnnotation;
            this.context = context;
        }

        @Override
        public String value() {
            return jdkAnnotation.value();
        }

        @Override
        public int priority() {
            return jdkAnnotation.priority();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return JdkLabel.class;
        }

        JdkLabel jdkAnnotation() {
            return jdkAnnotation;
        }

        ModelsContext context() {
            return context;
        }
    }
}
