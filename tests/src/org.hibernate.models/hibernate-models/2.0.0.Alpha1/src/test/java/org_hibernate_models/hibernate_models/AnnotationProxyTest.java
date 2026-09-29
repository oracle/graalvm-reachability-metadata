/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.AnnotationDescriptor;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationProxyTest {
    @Test
    public void createsProxyUsageFromDescriptorAttributeValues() {
        final ModelsContext context = newModelsContext();
        final AnnotationDescriptor<ProxyLabel> descriptor = context.getAnnotationDescriptorRegistry()
                .getDescriptor(ProxyLabel.class);
        final Map<String, Object> attributeValues = new HashMap<>();
        attributeValues.put("value", "hibernate-model");
        attributeValues.put("priority", 42);
        attributeValues.put("tags", new String[]{"metadata", "native-image"});

        final ProxyLabel usage = descriptor.createUsage(attributeValues, context);

        assertThat(usage).isInstanceOf(ProxyLabel.class);
        assertThat(usage.annotationType()).isEqualTo(ProxyLabel.class);
        assertThat(usage.value()).isEqualTo("hibernate-model");
        assertThat(usage.priority()).isEqualTo(42);
        assertThat(usage.tags()).containsExactly("metadata", "native-image");
        assertThat(usage.toString()).contains("AnnotationProxy", ProxyLabel.class.getName());
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ProxyLabel {
        String value();

        int priority();

        String[] tags();
    }
}
