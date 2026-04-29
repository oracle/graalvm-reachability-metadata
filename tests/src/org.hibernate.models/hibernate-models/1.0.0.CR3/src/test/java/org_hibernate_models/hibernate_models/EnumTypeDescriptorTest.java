/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.EnumTypeDescriptor;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumTypeDescriptorTest {
    @Test
    public void createsArrayForEnumValueType() {
        final EnumTypeDescriptor<ModelState> descriptor = new EnumTypeDescriptor<>(ModelState.class);

        final ModelState[] states = descriptor.makeArray(3, newModelsContext());

        assertThat(states).hasSize(3);
        assertThat(states.getClass().getComponentType()).isEqualTo(ModelState.class);
        states[0] = ModelState.ACTIVE;
        states[1] = ModelState.ARCHIVED;
        assertThat(states).containsExactly(ModelState.ACTIVE, ModelState.ARCHIVED, null);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    private enum ModelState {
        ACTIVE,
        ARCHIVED
    }
}
