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
import org.hibernate.models.internal.NestedTypeDescriptor;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NestedTypeDescriptorTest {
    @Test
    public void createsArrayForNestedAnnotationValueType() {
        final NestedTypeDescriptor<NestedLabel> descriptor = new NestedTypeDescriptor<>(NestedLabel.class);

        final NestedLabel[] labels = descriptor.makeArray(2, newModelsContext());

        assertThat(labels).hasSize(2);
        assertThat(labels).isInstanceOf(NestedLabel[].class);
        assertThat(labels).containsExactly(null, null);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedLabel {
        String value() default "default";
    }
}
