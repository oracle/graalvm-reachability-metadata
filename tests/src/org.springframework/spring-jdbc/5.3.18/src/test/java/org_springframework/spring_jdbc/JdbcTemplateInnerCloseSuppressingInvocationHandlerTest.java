/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcTemplateInnerCloseSuppressingInvocationHandlerTest {
    @Test
    void configuresStatementsReturnedThroughCallbackConnection() {
        JdbcTemplate template = new JdbcTemplate(JdbcTestSupport.dataSource());
        template.setMaxRows(1);
        template.setFetchSize(2);
        template.setQueryTimeout(15);

        Integer value = template.execute((ConnectionCallback<Integer>) connection -> {
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.getMaxRows()).isEqualTo(1);
                assertThat(statement.getFetchSize()).isEqualTo(2);
                assertThat(statement.getQueryTimeout()).isEqualTo(15);
                try (ResultSet rows = statement.executeQuery("SELECT X FROM SYSTEM_RANGE(1, 3)")) {
                    assertThat(rows.next()).isTrue();
                    int first = rows.getInt(1);
                    assertThat(rows.next()).isFalse();
                    return first;
                }
            }
        });
        assertThat(value).isEqualTo(1);
    }
}
