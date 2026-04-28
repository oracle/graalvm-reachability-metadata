/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class StatementFacadeTest {

    @Test
    void wrapsCreatedStatementsWithStatementFacadeProxy() throws SQLException {
        DataSource dataSource = new DataSource(createPoolProperties());
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE statement_facade_item (
                        id INT NOT NULL PRIMARY KEY,
                        name VARCHAR(255)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO statement_facade_item(id, name)
                    VALUES (1, 'facade-statement')
                    """);

            assertInsertedItem(statement);
            assertThat(statement.toString()).contains("StatementFacade");
        } finally {
            dataSource.close(true);
        }
    }

    private PoolProperties createPoolProperties() {
        PoolProperties poolProperties = new PoolProperties();
        poolProperties.setDriverClassName("org.h2.Driver");
        poolProperties.setUrl("jdbc:h2:mem:statement_facade;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        poolProperties.setUsername("sa");
        poolProperties.setPassword("");
        poolProperties.setInitialSize(0);
        poolProperties.setMaxActive(1);
        poolProperties.setJmxEnabled(false);
        poolProperties.setTimeBetweenEvictionRunsMillis(0);
        poolProperties.setUseStatementFacade(true);
        return poolProperties;
    }

    private void assertInsertedItem(Statement statement) throws SQLException {
        String query = "SELECT name FROM statement_facade_item WHERE id = 1";
        try (ResultSet resultSet = statement.executeQuery(query)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("facade-statement");
            assertThat(resultSet.next()).isFalse();
        }
    }
}
