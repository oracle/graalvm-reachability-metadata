/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_core_jakarta;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Configuration;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.ValidatorProcessor;
import io.swagger.v3.oas.annotations.parameters.ValidatedParameter;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelResolverTest implements ValidatorProcessor {
    @Test
    void resolvesEnumSchemaFromJsonValueField() {
        ResolvedSchema resolvedSchema = new ModelConverters().readAllAsResolvedSchema(EnumContainer.class);

        Schema<?> status = property(resolvedSchema.schema, "status");

        List<?> enumValues = status.getEnum();
        assertThat(enumValues).hasSize(2);
        assertThat(enumValues.get(0)).isEqualTo("active");
        assertThat(enumValues.get(1)).isEqualTo("archived");
    }

    @Test
    void resolvesRecordContainerPropertyAnnotations() {
        ResolvedSchema resolvedSchema = new ModelConverters().readAllAsResolvedSchema(RecordContainer.class);

        Schema<?> tags = property(resolvedSchema.schema, "tags");

        assertThat(tags.getItems()).isNotNull();
        assertThat(tags.getItems().getType()).isEqualTo("string");
    }

    @Test
    void resolvesValidationInvocationGroupsFromSpringAndSwaggerAnnotations() {
        Annotation[] invocationAnnotations = new Annotation[] {
                new SpringValidatedAnnotation(AdminValidationGroup.class),
                new SwaggerValidatedParameterAnnotation(UserValidationGroup.class)
        };
        AnnotatedType annotatedType = new AnnotatedType()
                .type(ValidatedContainer.class)
                .ctxAnnotations(invocationAnnotations);

        ResolvedSchema resolvedSchema = new ModelConverters().readAllAsResolvedSchema(annotatedType);

        assertThat(resolvedSchema.schema.getRequired())
                .contains("adminValue", "userValue")
                .doesNotContain("otherValue");
    }

    @Test
    void acceptsValidatorProcessorConfigurationClassName() {
        Configuration configuration = new Configuration()
                .validatorProcessorClass(ModelResolverTest.class.getName());

        ModelResolver resolver = new ModelResolver(Json.mapper()).configuration(configuration);

        assertThat(resolver.getConfiguration()).isSameAs(configuration);
    }

    private static Schema<?> property(Schema<?> schema, String propertyName) {
        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties).containsKey(propertyName);
        return properties.get(propertyName);
    }

    public static class EnumContainer {
        public JsonValueFieldStatus status;
    }

    public enum JsonValueFieldStatus {
        ACTIVE("active"),
        ARCHIVED("archived");

        @JsonValue
        public final String wireValue;

        JsonValueFieldStatus(String wireValue) {
            this.wireValue = wireValue;
        }
    }

    public record RecordContainer(List<@NotNull String> tags) {
    }

    public static class ValidatedContainer {
        @NotNull(groups = AdminValidationGroup.class)
        public String adminValue;

        @NotNull(groups = UserValidationGroup.class)
        public String userValue;

        @NotNull(groups = OtherValidationGroup.class)
        public String otherValue;
    }

    public interface AdminValidationGroup {
    }

    public interface UserValidationGroup {
    }

    public interface OtherValidationGroup {
    }

    public static class SpringValidatedAnnotation implements Validated {
        private final Class<?>[] groups;

        SpringValidatedAnnotation(Class<?>... groups) {
            this.groups = groups.clone();
        }

        @Override
        public Class<?>[] value() {
            return groups.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Validated.class;
        }
    }

    public static class SwaggerValidatedParameterAnnotation implements ValidatedParameter {
        private final Class<?>[] groups;

        SwaggerValidatedParameterAnnotation(Class<?>... groups) {
            this.groups = groups.clone();
        }

        @Override
        public Class<?>[] value() {
            return groups.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return ValidatedParameter.class;
        }
    }
}
