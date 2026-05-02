/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_json;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.json.JsonInput;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticInitializerCoercerTest {
    private final Json json = new Json();

    @Test
    public void shouldDeserializeUsingStaticFromJsonFactoryWithMapArgument() {
        FactoryBackedEvent event = json.toType(
            """
            {
              "name": "download",
              "retryCount": 3
            }
            """,
            FactoryBackedEvent.class);

        assertThat(event.getName()).isEqualTo("download");
        assertThat(event.getRetryCount()).isEqualTo(3);
    }

    @Test
    public void shouldDeserializeUsingStaticFromJsonFactoryWithJsonInputArgument() {
        InputBackedEvent event = json.toType(
            """
            {
              "name": "upload",
              "enabled": true
            }
            """,
            InputBackedEvent.class);

        assertThat(event.getName()).isEqualTo("upload");
        assertThat(event.isEnabled()).isTrue();
    }

    public static class FactoryBackedEvent {
        private final String name;
        private final int retryCount;

        private FactoryBackedEvent(String name, int retryCount) {
            this.name = name;
            this.retryCount = retryCount;
        }

        public static FactoryBackedEvent fromJson(Map<String, Object> values) {
            return new FactoryBackedEvent(
                (String) values.get("name"),
                ((Number) values.get("retryCount")).intValue());
        }

        public String getName() {
            return name;
        }

        public int getRetryCount() {
            return retryCount;
        }
    }

    public static class InputBackedEvent {
        private final String name;
        private final boolean enabled;

        private InputBackedEvent(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }

        public static InputBackedEvent fromJson(JsonInput input) {
            String name = null;
            boolean enabled = false;

            input.beginObject();
            while (input.hasNext()) {
                String property = input.nextName();
                if ("name".equals(property)) {
                    name = input.nextString();
                } else if ("enabled".equals(property)) {
                    enabled = input.nextBoolean();
                } else {
                    input.skipValue();
                }
            }
            input.endObject();

            return new InputBackedEvent(name, enabled);
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
