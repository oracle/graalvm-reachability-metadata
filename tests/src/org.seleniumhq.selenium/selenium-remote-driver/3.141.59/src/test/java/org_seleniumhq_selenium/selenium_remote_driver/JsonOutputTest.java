/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.json.Json;

public class JsonOutputTest {
    @Test
    void usesPrivateToJsonMethodWhenSerializing() {
        String json = new Json().toJson(new CustomJsonValue("converted"));

        assertTrue(json.contains("\"value\": \"converted\""));
    }

    public static class CustomJsonValue {
        private final String value;

        public CustomJsonValue(String value) {
            this.value = value;
        }

        private Map<String, Object> toJson() {
            return Collections.singletonMap("value", value);
        }
    }
}
