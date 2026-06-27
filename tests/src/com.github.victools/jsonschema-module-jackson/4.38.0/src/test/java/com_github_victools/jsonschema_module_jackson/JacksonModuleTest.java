/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_victools.jsonschema_module_jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonModuleTest {

    @Test
    void jsonNamingAnnotationAppliesAnnotatedPropertyNamingStrategy() {
        JsonNode schema = generateSchema(KebabCaseModel.class);

        JsonNode properties = schema.path("properties");
        assertThat(properties.has("display-name")).isTrue();
        assertThat(properties.has("displayName")).isFalse();
        assertThat(properties.path("display-name").path("type").asText()).isEqualTo("string");
    }

    private static JsonNode generateSchema(Class<?> targetType) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule());
        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);
        return generator.generateSchema(targetType);
    }

    @JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
    public static class KebabCaseModel {
        public String displayName;
    }
}
