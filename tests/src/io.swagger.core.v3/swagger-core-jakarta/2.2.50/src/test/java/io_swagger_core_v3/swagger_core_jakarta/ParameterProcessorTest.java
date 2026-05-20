/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_core_jakarta;

import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.PathParam;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterProcessorTest {
    @Test
    void appliesJakartaFormParamValue() {
        Parameter parameter = apply(new FormParamAnnotation("form-name"));

        assertThat(parameter.getName()).isEqualTo("form-name");
        assertThat(parameter.getIn()).isEqualTo("form");
    }

    @Test
    void appliesMultipartFormDataParamValue() {
        Parameter parameter = apply(new FormDataParamAnnotation("file-part"));

        assertThat(parameter.getName()).isEqualTo("file-part");
        assertThat(parameter.getIn()).isEqualTo("form");
    }

    @Test
    void appliesJakartaPathParamValue() {
        Parameter parameter = apply(new PathParamAnnotation("resource-id"));

        assertThat(parameter.getName()).isEqualTo("resource-id");
    }

    private static Parameter apply(Annotation annotation) {
        return ParameterProcessor.applyAnnotations(
                new Parameter(),
                String.class,
                List.of(annotation),
                new Components(),
                new String[0],
                new String[0],
                null);
    }

    private static final class FormParamAnnotation implements FormParam {
        private final String value;

        FormParamAnnotation(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return FormParam.class;
        }
    }

    private static final class FormDataParamAnnotation implements FormDataParam {
        private final String value;

        FormDataParamAnnotation(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return FormDataParam.class;
        }
    }

    private static final class PathParamAnnotation implements PathParam {
        private final String value;

        PathParamAnnotation(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return PathParam.class;
        }
    }
}
