/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.util.ObjectUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ObjectUtilTest {

    @Test
    void getPropertyReadsBeanValueThroughPublicGetter() {
        InspectableObject object = new InspectableObject("ready", 12);

        assertEquals("ready", ObjectUtil.getProperty(object, "state"));
        assertEquals(Integer.valueOf(12), ObjectUtil.getProperty(object, "priority"));
    }

    @Test
    void convertResolvesClassNameForSubsequentTypedConversion() {
        Class<?> targetType = ObjectUtil.convert("java.lang.Integer", Class.class);

        Object convertedValue = ObjectUtil.convert("42", targetType);

        assertSame(Integer.class, convertedValue.getClass());
        assertEquals(42, ((Number) convertedValue).intValue());
    }

    public static class InspectableObject {
        private final String state;
        private final Integer priority;

        public InspectableObject(String state, Integer priority) {
            this.state = state;
            this.priority = priority;
        }

        public String getState() {
            return state;
        }

        public Integer getPriority() {
            return priority;
        }
    }
}
