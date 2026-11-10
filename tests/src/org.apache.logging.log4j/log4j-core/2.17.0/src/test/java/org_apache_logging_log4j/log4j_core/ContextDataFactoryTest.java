/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContextDataFactoryTest {
    @Test
    void constructsTheConfiguredContextMapWithBothConstructorForms() {
        StringMap defaultMap = ContextDataFactory.createContextData();
        StringMap sizedMap = ContextDataFactory.createContextData(16);
        assertInstanceOf(ApplicationContextData.class, defaultMap);
        assertInstanceOf(ApplicationContextData.class, sizedMap);
        defaultMap.putValue("request", "abc");
        sizedMap.putAll(defaultMap);
        assertEquals("abc", sizedMap.getValue("request"));
        assertTrue(ContextDataFactory.emptyFrozenContextData().isFrozen());
    }

    public static class ApplicationContextData extends SortedArrayStringMap {
        public ApplicationContextData() {
            super();
        }

        public ApplicationContextData(int capacity) {
            super(capacity);
        }
    }
}
