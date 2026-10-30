/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import com.ibatis.common.jdbc.logging.PreparedStatementLogProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class PreparedStatementLogProxyTest {
    @Test
    void bindsParametersAndExecutesUpdatesAndQueries() throws Exception {
        try (Connection connection = JdbcTestSupport.openConnection();
                Statement schema = connection.createStatement()) {
            schema.execute("CREATE TABLE notes (id INT PRIMARY KEY, message VARCHAR(40))");
            String insertSql = "INSERT INTO notes VALUES (?, ?)";
            try (PreparedStatement insert = PreparedStatementLogProxy.newInstance(
                    connection.prepareStatement(insertSql), insertSql)) {
                insert.setQueryTimeout(10);
                insert.setInt(1, 1);
                insert.setString(2, "first");
                assertThat(insert.executeUpdate()).isEqualTo(1);
                insert.setInt(1, 2);
                insert.setNull(2, Types.VARCHAR);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            String querySql = "SELECT message FROM notes WHERE id = ?";
            try (PreparedStatement query = PreparedStatementLogProxy.newInstance(
                    connection.prepareStatement(querySql), querySql)) {
                query.setInt(1, 1);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("message")).isEqualTo("first");
                    assertThat(rows.next()).isFalse();
                }
                query.setInt(1, 2);
                assertThat(query.execute()).isTrue();
                try (ResultSet rows = query.getResultSet()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("message")).isNull();
                    assertThat(rows.wasNull()).isTrue();
                }
            }
        }
    }
}
