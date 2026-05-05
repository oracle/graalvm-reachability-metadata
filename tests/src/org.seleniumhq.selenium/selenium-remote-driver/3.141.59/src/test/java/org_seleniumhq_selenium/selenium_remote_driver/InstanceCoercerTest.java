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
import org.openqa.selenium.json.PropertySetting;

public class InstanceCoercerTest {
    @Test
    void readsObjectsByPrivateFieldsAndBeanSetters() {
        Json json = new Json();

        FieldConfigured fieldConfigured = json.toType(
                "{\"name\":\"Ada\",\"count\":3}",
                FieldConfigured.class,
                PropertySetting.BY_FIELD);
        BeanConfigured beanConfigured = json.toType(
                "{\"name\":\"Grace\",\"count\":7}",
                BeanConfigured.class,
                PropertySetting.BY_NAME);

        assertEquals("Ada", fieldConfigured.name);
        assertEquals(3, fieldConfigured.count);
        assertEquals("Grace", beanConfigured.name);
        assertEquals(7, beanConfigured.count);
    }

    public static class FieldConfigured {
        private String name;
        private int count;

        private FieldConfigured() {
        }
    }

    public static class BeanConfigured {
        private String name;
        private int count;

        public BeanConfigured() {
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
