/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionProxy;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcTemplateTest {
    @Test
    void keepsCallbackConnectionUsableUntilTemplateReleasesIt() throws Exception {
        JdbcTestSupport.TrackingDataSource source = new JdbcTestSupport.TrackingDataSource();
        JdbcTemplate template = new JdbcTemplate(source);
        Integer result = template.execute((ConnectionCallback<Integer>) connection -> {
            assertThat(connection).isInstanceOf(ConnectionProxy.class);
            assertThat(((ConnectionProxy) connection).getTargetConnection()).isSameAs(source.lastConnection());
            connection.close();
            assertThat(connection.isClosed()).isFalse();
            return JdbcTestSupport.queryInt(connection, "SELECT 41");
        });
        assertThat(result).isEqualTo(41);
        assertThat(source.connectionCount()).isEqualTo(1);
        assertThat(source.lastConnection().isClosed()).isTrue();
    }
}
