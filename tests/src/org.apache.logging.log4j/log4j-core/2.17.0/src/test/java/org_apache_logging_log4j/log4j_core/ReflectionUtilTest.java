/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.lang.reflect.Field;

import org.apache.logging.log4j.core.util.ReflectionUtil;
import org.apache.logging.log4j.core.util.TypeUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectionUtilTest {
    @Test
    void instantiatesAndUpdatesApplicationSettings() {
        Settings settings = ReflectionUtil.instantiate(Settings.class);
        Field field = TypeUtil.getAllDeclaredFields(Settings.class).getFirst();
        assertEquals("initial", ReflectionUtil.getFieldValue(field, settings));
        ReflectionUtil.setFieldValue(field, settings, "configured");
        assertEquals("configured", settings.destination);
    }

    public static class Settings {
        private String destination = "initial";

        private Settings() {
        }
    }
}
