/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.json.Json;

public class StaticInitializerCoercerTest {
    @Test
    void createsValueWithStaticFromJsonFactory() {
        FromJsonValue value = new Json().toType("\"factory-value\"", FromJsonValue.class);

        assertEquals("factory-value", value.value);
    }

    public static class FromJsonValue {
        private final String value;

        private FromJsonValue(String value) {
            this.value = value;
        }

        public static FromJsonValue fromJson(String value) {
            return new FromJsonValue(value);
        }
    }
}
