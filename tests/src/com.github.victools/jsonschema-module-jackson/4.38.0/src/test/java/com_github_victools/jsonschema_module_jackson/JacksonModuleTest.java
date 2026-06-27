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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonModuleTest {

    @Test
    void jsonNamingAnnotationAppliesDeclaredPropertyNamingStrategy() {
        ObjectNode schema = generateSchema(NamedProperties.class);

        JsonNode properties = schema.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("first_name")).isTrue();
        assertThat(properties.has("firstName")).isFalse();
    }

    private static ObjectNode generateSchema(Class<?> targetType) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).with(new JacksonModule());
        SchemaGenerator generator = new SchemaGenerator(configBuilder.build());
        return generator.generateSchema(targetType);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class NamedProperties {
        public String firstName;
    }
}
