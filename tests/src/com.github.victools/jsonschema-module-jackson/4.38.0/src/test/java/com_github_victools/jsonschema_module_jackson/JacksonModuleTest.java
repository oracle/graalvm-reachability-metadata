/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_victools.jsonschema_module_jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonModuleTest {

    @Test
    void jsonNamingStrategyRenamesFieldInGeneratedSchema() {
        JsonNode schema = generateSchema(JsonNamingAnnotatedModel.class);
        JsonNode properties = schema.get("properties");

        assertThat(properties).isNotNull();
        assertThat(properties.has("firstName")).isFalse();
        assertThat(properties.has("json_firstName")).isTrue();
        assertThat(properties.get("json_firstName").get("type").asText()).isEqualTo("string");
    }

    private static JsonNode generateSchema(Class<?> type) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule());
        SchemaGenerator generator = new SchemaGenerator(configBuilder.build());
        return generator.generateSchema(type);
    }

    @JsonNaming(PrefixNamingStrategy.class)
    public static class JsonNamingAnnotatedModel {
        public String firstName;
    }

    public static class PrefixNamingStrategy extends PropertyNamingStrategy {

        public PrefixNamingStrategy() {
        }

        @Override
        public String nameForField(MapperConfig<?> config, AnnotatedField field, String defaultName) {
            return "json_" + defaultName;
        }
    }
}
