/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_json;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonOutputTest {
    private final Json json = new Json();

    @Test
    public void shouldSerializeObjectsUsingDeclaredToJsonMethod() {
        String serialized = json.toJson(new JsonBackedEvent("download", 2, true));

        assertThat(serialized)
            .contains("\"kind\": \"download\"")
            .contains("\"retryCount\": 2")
            .contains("\"enabled\": true");
    }

    @Test
    public void shouldSerializeObjectsUsingDeclaredAsMapMethod() {
        AsMapBackedEvent event = new AsMapBackedEvent("upload", List.of("fast", "verified"));

        String serialized = json.toJson(event);

        assertThat(serialized)
            .contains("\"kind\": \"upload\"")
            .contains("\"labels\": [")
            .contains("\"fast\"")
            .contains("\"verified\"");
    }

    @Test
    public void shouldSerializeObjectsUsingDeclaredToMapMethod() {
        String serialized = json.toJson(new ToMapBackedEvent("queued", Map.of("priority", "high")));

        assertThat(serialized)
            .contains("\"state\": \"queued\"")
            .contains("\"metadata\": {")
            .contains("\"priority\": \"high\"");
    }

    public static class JsonBackedEvent {
        private final String kind;
        private final int retryCount;
        private final boolean enabled;

        public JsonBackedEvent(String kind, int retryCount, boolean enabled) {
            this.kind = kind;
            this.retryCount = retryCount;
            this.enabled = enabled;
        }

        public Map<String, Object> toJson() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("kind", kind);
            values.put("retryCount", retryCount);
            values.put("enabled", enabled);
            return values;
        }
    }

    public static class AsMapBackedEvent {
        private final String kind;
        private final List<String> labels;

        public AsMapBackedEvent(String kind, List<String> labels) {
            this.kind = kind;
            this.labels = labels;
        }

        public Map<String, Object> asMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("kind", kind);
            values.put("labels", labels);
            return values;
        }
    }

    public static class ToMapBackedEvent {
        private final String state;
        private final Map<String, Object> metadata;

        public ToMapBackedEvent(String state, Map<String, Object> metadata) {
            this.state = state;
            this.metadata = metadata;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("state", state);
            values.put("metadata", metadata);
            return values;
        }
    }
}
