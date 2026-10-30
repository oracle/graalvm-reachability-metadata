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

import com.ibatis.common.jdbc.logging.StatementLogProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class StatementLogProxyTest {
    @Test
    void executesSqlAndReturnsLoggedResultSets() throws Exception {
        try (Connection connection = JdbcTestSupport.openConnection();
                Statement statement = StatementLogProxy.newInstance(connection.createStatement())) {
            statement.setQueryTimeout(10);
            assertThat(statement.getQueryTimeout()).isEqualTo(10);
            statement.execute("CREATE TABLE scores (score INT)");
            assertThat(statement.executeUpdate("INSERT INTO scores VALUES (17)")).isEqualTo(1);
            try (ResultSet rows = statement.executeQuery("SELECT score FROM scores")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("score")).isEqualTo(17);
                assertThat(rows.next()).isFalse();
            }
            assertThat(statement.execute("SELECT COUNT(*) AS total FROM scores")).isTrue();
            try (ResultSet rows = statement.getResultSet()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("total")).isEqualTo(1);
            }
        }
    }
}
