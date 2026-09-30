/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.util.serializer.JacksonAdapter;
import com.azure.core.util.serializer.SerializerEncoding;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class AdditionalPropertiesSerializerAnonymous1Test {
    @Test
    void expandsAdditionalPropertiesIntoTheSerializedObject() throws Exception {
        ExtensibleModel model = new ExtensibleModel();
        model.id = "7";
        model.additionalProperties = new LinkedHashMap<>(Map.of("extension", true));

        String json = new JacksonAdapter().serialize(model, SerializerEncoding.JSON);

        assertThat(json).contains("\"id\":\"7\"").contains("\"extension\":true");
        assertThat(json).doesNotContain("additionalProperties");
    }

    public static final class ExtensibleModel {
        @JsonProperty("id")
        private String id;

        @JsonProperty
        private Map<String, Object> additionalProperties;

        public String getId() {
            return id;
        }

        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }
    }
}
