/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.asm.Advice.OffsetMapping.Factory;
import org.modelmapper.internal.bytebuddy.asm.Advice.OffsetMapping.ForStackManipulation.OfAnnotationProperty;

public class AdviceInnerOffsetMappingInnerForStackManipulationInnerOfAnnotationPropertyTest {
    @Test
    void createsOffsetMappingFactoryForAnnotationProperty() {
        Factory<CustomAdviceBinding> factory = OfAnnotationProperty.of(CustomAdviceBinding.class, "value");

        assertThat(factory.getAnnotationType()).isEqualTo(CustomAdviceBinding.class);
    }

    private @interface CustomAdviceBinding {
        String value();
    }
}
