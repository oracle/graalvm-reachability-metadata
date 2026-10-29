/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import com.ibatis.common.jdbc.logging.ConnectionLogProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ConnectionLogProxyTest {
    @Test
    void delegatesTransactionsAndWrapsEveryStatementKind() throws Exception {
        try (Connection connection = ConnectionLogProxy.newInstance(JdbcTestSupport.openConnection())) {
            connection.setAutoCommit(false);
            assertThat(connection.getAutoCommit()).isFalse();
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE entries (id INT PRIMARY KEY, title VARCHAR(30))");
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO entries VALUES (?, ?)")) {
                insert.setInt(1, 8);
                insert.setString(2, "committed");
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
            try (CallableStatement call = connection.prepareCall("CALL ABS(?)")) {
                call.setInt(1, -8);
                try (ResultSet result = call.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(8);
                }
            }
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("DELETE FROM entries")).isEqualTo(1);
            }
            connection.rollback();
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT title FROM entries WHERE id = 8")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("committed");
            }
        }
    }
}
