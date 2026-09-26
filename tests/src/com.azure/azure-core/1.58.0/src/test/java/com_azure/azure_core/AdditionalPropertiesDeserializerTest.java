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
public class AdditionalPropertiesDeserializerTest {
    @Test
    void collectsUnknownJsonMembersAsAdditionalProperties() throws Exception {
        AdditionalModel decoded = new JacksonAdapter().deserialize(
                "{\"name\":\"widget\",\"color\":\"blue\",\"rank\":3}",
                AdditionalModel.class,
                SerializerEncoding.JSON);

        assertThat(decoded.name).isEqualTo("widget");
        assertThat(decoded.additionalProperties).containsEntry("color", "blue").containsEntry("rank", 3);
    }

    public static final class AdditionalModel {
        @JsonProperty("name")
        private String name;

        @JsonProperty
        private Map<String, Object> additionalProperties;

        public String getName() {
            return name;
        }

        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }
    }
}
