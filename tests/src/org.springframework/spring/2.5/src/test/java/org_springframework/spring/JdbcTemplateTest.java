/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

public class JdbcTemplateTest {
    @Test
    void executesConnectionCallbackWithCloseSuppressingProxy() {
        JdbcTemplate template = new JdbcTemplate(TestDatabase.source());
        template.setQueryTimeout(10);
        Object result = template.execute((ConnectionCallback) connection -> {
            connection.close();
            assertFalse(connection.isClosed());
            return TestDatabase.query(connection);
        });
        assertEquals(42, result);
    }
}
