/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_victools.jsonschema_module_jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
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
    void jsonNamingAnnotationRenamesDeclaredFields() {
        JsonNode schema = generateSchema(NamingAnnotatedModel.class);

        JsonNode properties = schema.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("camel_case_value")).isTrue();
        assertThat(properties.has("camelCaseValue")).isFalse();
    }

    private static JsonNode generateSchema(Class<?> schemaType) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule());
        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);
        return generator.generateSchema(schemaType);
    }

    @JsonNaming(SnakeCaseStrategy.class)
    public static class NamingAnnotatedModel {
        public String camelCaseValue;
    }
}
