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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class AdditionalPropertiesDeserializerAnonymous1Test {
    @Test
    void installsAdditionalPropertyDeserializationForAnnotatedModels() throws Exception {
        ExtensibleModel decoded = new JacksonAdapter().deserialize(
                "{\"id\":\"7\",\"extension\":true}", ExtensibleModel.class, SerializerEncoding.JSON);

        assertThat(decoded.id).isEqualTo("7");
        assertThat(decoded.additionalProperties).containsEntry("extension", true);
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
