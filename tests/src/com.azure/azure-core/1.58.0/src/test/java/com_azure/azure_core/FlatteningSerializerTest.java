/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.annotation.JsonFlatten;
import com.azure.core.util.serializer.JacksonAdapter;
import com.azure.core.util.serializer.SerializerEncoding;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class FlatteningSerializerTest {
    @Test
    void serializesFlattenedFieldsWhilePreservingPeriodsInMapKeys() throws Exception {
        FlattenedWidget widget = new FlattenedWidget();
        widget.name = "azure";
        widget.labels = new LinkedHashMap<>(Map.of("region.name", "west"));

        String json = new JacksonAdapter().serialize(widget, SerializerEncoding.JSON);

        assertThat(json).contains("\"profile\"");
        assertThat(json).contains("\"name\":\"azure\"");
        assertThat(json).contains("\"region.name\":\"west\"");
    }

    @JsonFlatten
    public static final class FlattenedWidget {
        @JsonProperty("profile.name")
        private String name;

        @JsonProperty("profile.labels")
        private Map<String, String> labels;

        public String getName() {
            return name;
        }

        public Map<String, String> getLabels() {
            return labels;
        }
    }
}
