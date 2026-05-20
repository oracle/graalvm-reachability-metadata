/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_core_jakarta;

import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.ws.rs.DefaultValue;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterProcessorInnerAnnotationsHelperTest {
    @Test
    void appliesJakartaDefaultValueFromAnnotationHelper() {
        Parameter parameter = new Parameter().schema(new StringSchema());

        Parameter processedParameter = ParameterProcessor.applyAnnotations(
                parameter,
                String.class,
                List.of(new DefaultValueAnnotation("fallback")),
                new Components(),
                new String[0],
                new String[0],
                null);

        assertThat(processedParameter.getSchema().getDefault()).isEqualTo("fallback");
    }

    private static final class DefaultValueAnnotation implements DefaultValue {
        private final String value;

        DefaultValueAnnotation(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return DefaultValue.class;
        }
    }
}
