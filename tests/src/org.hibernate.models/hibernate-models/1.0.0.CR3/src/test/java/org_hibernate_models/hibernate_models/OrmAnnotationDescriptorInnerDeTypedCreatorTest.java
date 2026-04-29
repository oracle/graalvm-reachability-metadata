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
import java.util.Map;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.OrmAnnotationDescriptor;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrmAnnotationDescriptorInnerDeTypedCreatorTest {
    @Test
    public void createsMutableAnnotationUsageFromAttributeValueMap() {
        final ModelsContext context = newModelsContext();
        final OrmAnnotationDescriptor<DetypedLabel, DetypedLabelUsage> descriptor = new OrmAnnotationDescriptor<>(
                DetypedLabel.class,
                DetypedLabelUsage.class
        );
        final Map<String, Object> attributeValues = Map.of(
                "value", "entity-name",
                "priority", 7
        );

        final DetypedLabel usage = descriptor.createUsage(attributeValues, context);

        assertThat(usage).isInstanceOf(DetypedLabelUsage.class);
        assertThat(usage.value()).isEqualTo("entity-name");
        assertThat(usage.priority()).isEqualTo(7);
        assertThat(usage.annotationType()).isEqualTo(DetypedLabel.class);
        assertThat(((DetypedLabelUsage) usage).context()).isSameAs(context);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface DetypedLabel {
        String value();

        int priority() default 0;
    }

    public static final class DetypedLabelUsage implements DetypedLabel {
        private final Map<String, ?> attributeValues;
        private final ModelsContext context;

        public DetypedLabelUsage(Map<String, ?> attributeValues, ModelsContext context) {
            this.attributeValues = attributeValues;
            this.context = context;
        }

        @Override
        public String value() {
            return (String) attributeValues.get("value");
        }

        @Override
        public int priority() {
            return (Integer) attributeValues.get("priority");
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return DetypedLabel.class;
        }

        ModelsContext context() {
            return context;
        }
    }
}
