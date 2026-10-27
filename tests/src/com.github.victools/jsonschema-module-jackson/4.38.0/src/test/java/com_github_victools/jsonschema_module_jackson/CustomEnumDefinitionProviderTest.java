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
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomEnumDefinitionProviderTest {

    @Test
    void jsonValueAnnotatedEnumMethodDefinesSerializedEnumValues() {
        JsonNode schema = generateSchema(JsonValueBackedStatus.class, JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(schema.get("enum")).extracting(JsonNode::asText)
                .containsExactly("new-ticket", "closed-ticket");
    }

    @Test
    void jsonPropertyAnnotatedEnumConstantsDefineSerializedEnumValues() {
        JsonNode schema = generateSchema(JsonPropertyBackedPriority.class,
                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(schema.get("enum")).extracting(JsonNode::asText)
                .containsExactly("normal-priority", "urgent-priority");
    }

    private static JsonNode generateSchema(Class<?> type, JacksonOption option) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule(option));
        SchemaGenerator generator = new SchemaGenerator(configBuilder.build());
        return generator.generateSchema(type);
    }

    public enum JsonValueBackedStatus {
        NEW("new-ticket"),
        CLOSED("closed-ticket");

        private final String wireValue;

        JsonValueBackedStatus(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String getWireValue() {
            return this.wireValue;
        }
    }

    public enum JsonPropertyBackedPriority {
        @JsonProperty("normal-priority")
        NORMAL,

        @JsonProperty("urgent-priority")
        URGENT
    }
}
