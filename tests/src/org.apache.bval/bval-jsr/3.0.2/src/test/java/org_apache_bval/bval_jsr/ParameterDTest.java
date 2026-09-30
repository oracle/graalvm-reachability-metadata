/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.metadata.MethodDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterDTest {

    @Test
    void resolvesGenericArrayParameterType() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            MethodDescriptor method = factory.getValidator().getConstraintsForClass(GenericService.class)
                    .getConstraintsForMethod("consume", Object[].class);

            assertThat(method.getParameterDescriptors()).singleElement()
                    .satisfies(parameter -> assertThat(parameter.getElementClass()).isEqualTo(Object[].class));
        }
    }

    public static class GenericService {
        public <T> void consume(@NotNull T[] values) {
        }
    }
}
