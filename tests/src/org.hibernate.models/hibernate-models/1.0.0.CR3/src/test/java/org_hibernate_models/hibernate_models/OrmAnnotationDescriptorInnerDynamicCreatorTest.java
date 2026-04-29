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

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.OrmAnnotationDescriptor;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrmAnnotationDescriptorInnerDynamicCreatorTest {
    @Test
    public void createsMutableAnnotationUsageFromModelsContextConstructor() {
        final ModelsContext context = newModelsContext();
        final OrmAnnotationDescriptor<DynamicLabel, DynamicLabelUsage> descriptor = new OrmAnnotationDescriptor<>(
                DynamicLabel.class,
                DynamicLabelUsage.class
        );

        final DynamicLabel usage = descriptor.createUsage(context);

        assertThat(usage).isInstanceOf(DynamicLabelUsage.class);
        assertThat(usage.value()).isEqualTo("created-from-context");
        assertThat(usage.annotationType()).isEqualTo(DynamicLabel.class);
        assertThat(((DynamicLabelUsage) usage).context()).isSameAs(context);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface DynamicLabel {
        String value() default "created-from-context";
    }

    public static final class DynamicLabelUsage implements DynamicLabel {
        private final ModelsContext context;

        public DynamicLabelUsage(ModelsContext context) {
            this.context = context;
        }

        @Override
        public String value() {
            return "created-from-context";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return DynamicLabel.class;
        }

        ModelsContext context() {
            return context;
        }
    }
}
