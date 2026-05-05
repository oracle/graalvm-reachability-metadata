/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.json.Json;

public class SimplePropertyDescriptorTest {
    @Test
    void invokesBeanGetterDuringObjectSerialization() {
        String json = new Json().toJson(new ReadableBean("ready"));

        assertTrue(json.contains("\"state\": \"ready\""));
    }

    public static class ReadableBean {
        private final String state;

        public ReadableBean(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }
}
