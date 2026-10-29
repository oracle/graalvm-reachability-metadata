/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import com.ibatis.common.jdbc.SimpleDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class SimpleDataSourceInnerSimplePooledConnectionTest {
    @Test
    void returnsConnectionsToThePoolAndRollsBackUncommittedWork() throws Exception {
        SimpleDataSource source = new SimpleDataSource(Map.of(
                "JDBC.Driver", "org.h2.Driver",
                "JDBC.ConnectionURL", "jdbc:h2:mem:" + UUID.randomUUID(),
                "JDBC.Username", "sa",
                "JDBC.Password", "",
                "Pool.MaximumActiveConnections", "1",
                "Pool.MaximumIdleConnections", "1",
                "Pool.TimeToWait", "10000"));
        try {
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement()) {
                assertThat(connection.getAutoCommit()).isFalse();
                statement.execute("CREATE TABLE pooled_notes (id INT PRIMARY KEY)");
                assertThat(statement.executeUpdate("INSERT INTO pooled_notes VALUES (1)")).isEqualTo(1);
                connection.commit();
                assertThat(statement.executeUpdate("INSERT INTO pooled_notes VALUES (2)")).isEqualTo(1);
            }
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT id FROM pooled_notes ORDER BY id")) {
                assertThat(connection.isClosed()).isFalse();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.next()).isFalse();
            }
            assertThat(source.getRequestCount()).isEqualTo(2);
            assertThat(source.getBadConnectionCount()).isZero();
        } finally {
            source.forceCloseAll();
        }
    }
}
