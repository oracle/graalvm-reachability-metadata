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
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonModuleTest {

    @Test
    void jsonNamingStrategyRenamesFieldsInGeneratedSchema() {
        JsonNode schema = generateSchema(SnakeCasePayload.class);
        JsonNode properties = schema.get("properties");

        assertThat(properties.has("first_name")).isTrue();
        assertThat(properties.has("postal_code")).isTrue();
        assertThat(properties.has("firstName")).isFalse();
        assertThat(properties.has("postalCode")).isFalse();
    }

    private static JsonNode generateSchema(Class<?> schemaType) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule());
        return new SchemaGenerator(configBuilder.build()).generateSchema(schemaType);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class SnakeCasePayload {
        public String firstName;
        public String postalCode;
    }
}
