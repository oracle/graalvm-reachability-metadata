/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

public class JdbcTemplateInnerCloseSuppressingInvocationHandlerTest {
    @Test
    void appliesStatementSettingsThroughConnectionProxy() {
        JdbcTemplate template = new JdbcTemplate(TestDatabase.source());
        template.setQueryTimeout(10);
        template.setMaxRows(3);
        Object result = template.execute((ConnectionCallback) connection -> {
            try (Statement statement = connection.createStatement()) {
                assertEquals(10, statement.getQueryTimeout());
                assertEquals(3, statement.getMaxRows());
                try (ResultSet rows = statement.executeQuery("select 11")) {
                    assertTrue(rows.next());
                    return rows.getInt(1);
                }
            }
        });
        assertEquals(11, result);
    }
}
