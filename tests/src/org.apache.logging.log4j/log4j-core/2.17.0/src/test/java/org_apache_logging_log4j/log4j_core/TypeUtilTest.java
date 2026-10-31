/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.logging.log4j.core.util.TypeUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeUtilTest {
    @Test
    void discoversInheritedConfigurationFields() {
        List<String> names = TypeUtil.getAllDeclaredFields(ChildSettings.class).stream().map(Field::getName).toList();
        assertEquals(List.of("destination", "enabled"), names);
    }

    public static class ParentSettings {
        public boolean enabled;
    }

    public static class ChildSettings extends ParentSettings {
        public String destination;
    }
}
