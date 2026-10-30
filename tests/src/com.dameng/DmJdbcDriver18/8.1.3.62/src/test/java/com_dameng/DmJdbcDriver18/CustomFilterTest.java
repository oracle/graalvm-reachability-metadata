/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.driver.DmdbConnection;
import dm.jdbc.filter.custom.ICustom;
import dm.jdbc.internal.conf.DmProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CustomFilterTest {
    @Test
    void configuredPolicyInterceptsStatementPreparation() throws Exception {
        PreparationPolicy.before = null;
        PreparationPolicy.after = null;
        DmProperties properties = new DmProperties();
        properties.setProperty("customFilter", PreparationPolicy.class.getName());
        try (DmdbConnection connection = new DmdbConnection(properties)) {
            assertNull(connection.prepareStatement("delete from inventory"));
            assertEquals("delete from inventory", PreparationPolicy.before.get("sql"));
            assertEquals(Boolean.TRUE, PreparationPolicy.before.get("prepare"));
            assertEquals("delete from inventory", PreparationPolicy.after.get("sql"));
            assertNull(PreparationPolicy.after.get("statement"));
        }
    }

    public static class PreparationPolicy implements ICustom {
        private static Map<String, Object> before;
        private static Map<String, Object> after;

        public PreparationPolicy() {
        }

        @Override
        public boolean beforePrepare(Map<String, Object> context) {
            before = new HashMap<>(context);
            return false;
        }

        @Override
        public void afterPrepare(Map<String, Object> context) {
            after = new HashMap<>(context);
        }

        @Override
        public boolean beforeExecute(Map<String, Object> context) {
            return false;
        }

        @Override
        public void afterExecute(Map<String, Object> context) {
            after = new HashMap<>(context);
        }
    }
}
