/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_core;

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
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelResolverTest {
    @Test
    void resolvesEnumValuesFromJsonValueField() {
        ResolvedSchema resolvedSchema = new ModelConverters().readAllAsResolvedSchema(EnumContainer.class);

        Schema<?> status = property(resolvedSchema.schema, "status");

        List<?> enumValues = status.getEnum();
        assertThat(enumValues).hasSize(2);
        assertThat(enumValues.get(0)).isEqualTo("active");
        assertThat(enumValues.get(1)).isEqualTo("archived");
    }

    @Test
    void resolvesRecordComponentTypeAnnotations() {
        ResolvedSchema resolvedSchema = new ModelConverters().readAllAsResolvedSchema(RecordContainer.class);

        Schema<?> tags = property(resolvedSchema.schema, "tags");

        assertThat(tags.getItems()).isNotNull();
        assertThat(tags.getItems().getType()).isEqualTo("string");
        assertThat(tags.getItems().getMinLength()).isEqualTo(2);
        assertThat(tags.getItems().getMaxLength()).isEqualTo(8);
    }

    @Test
    void usesValidationGroupsFromSupportedInvocationAnnotations() {
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
    void createsConfiguredValidatorProcessor() {
        ConfiguredValidatorProcessor.constructed = false;
        Configuration configuration = new Configuration()
                .validatorProcessorClass(ConfiguredValidatorProcessor.class.getName());

        ModelResolver resolver = new ModelResolver(Json.mapper()).configuration(configuration);

        assertThat(resolver.getConfiguration()).isSameAs(configuration);
        assertThat(ConfiguredValidatorProcessor.constructed).isTrue();
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

    public record RecordContainer(List<@Size(min = 2, max = 8) String> tags) {
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

    public static final class SpringValidatedAnnotation implements Validated {
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

    public static final class SwaggerValidatedParameterAnnotation implements ValidatedParameter {
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

    public static class ConfiguredValidatorProcessor implements ValidatorProcessor {
        private static boolean constructed;

        public ConfiguredValidatorProcessor() {
            constructed = true;
        }
    }
}
