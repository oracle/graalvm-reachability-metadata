/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_victools.jsonschema_module_jackson;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomEnumDefinitionProviderTest {

    @Test
    void jsonValueAnnotatedEnumMethodDefinesFlattenedEnumValues() {
        JsonNode schema = generateSchema(JsonValueStatus.class,
                JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE);

        assertThat(schema.path("type").asText()).isEqualTo("string");
        assertThat(enumValues(schema)).containsExactly("new-ticket", "closed-ticket");
    }

    @Test
    void jsonPropertyAnnotatedEnumConstantsDefineFlattenedEnumValues() {
        JsonNode schema = generateSchema(JsonPropertyStatus.class,
                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY);

        assertThat(schema.path("type").asText()).isEqualTo("string");
        assertThat(enumValues(schema)).containsExactly("PENDING", "done-ticket");
    }

    private static JsonNode generateSchema(Class<?> targetType, JacksonOption option) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule(option));
        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);
        return generator.generateSchema(targetType);
    }

    private static List<String> enumValues(JsonNode schema) {
        List<String> values = new ArrayList<>();
        for (JsonNode enumValue : schema.path("enum")) {
            values.add(enumValue.asText());
        }
        return values;
    }

    public enum JsonValueStatus {
        NEW("new-ticket"),
        CLOSED("closed-ticket");

        private final String serializedValue;

        JsonValueStatus(String serializedValue) {
            this.serializedValue = serializedValue;
        }

        @JsonValue
        public String getSerializedValue() {
            return this.serializedValue;
        }
    }

    public enum JsonPropertyStatus {
        @JsonProperty
        PENDING,
        @JsonProperty("done-ticket")
        DONE
    }
}
