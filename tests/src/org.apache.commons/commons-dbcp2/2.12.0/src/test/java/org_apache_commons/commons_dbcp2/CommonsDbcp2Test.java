/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CommonsDbcp2Test {
    private BasicDataSource dataSource;
    private Connection connection;

    @BeforeEach
    public void setUp() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("driverClassName", "org.h2.Driver");
        properties.setProperty("url", "jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        properties.setProperty("username", "fred");
        properties.setProperty("password", "secret");
        properties.setProperty("initialSize", "2");
        properties.setProperty("minIdle", "1");
        properties.setProperty("testOnBorrow", "true");
        properties.setProperty("testOnConnect", "true");
        properties.setProperty("validationQuery", "select 1");
        BasicDataSourceFactory dataSourceFactory = new BasicDataSourceFactory();
        dataSource = dataSourceFactory.createDataSource(properties);
        connection = dataSource.getConnection();
    }

    @AfterEach
    public void tearDown() throws Exception {
        dataSource.close();
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    @Test
    void testPreparedStatement() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE person (id INT NOT NULL, name VARCHAR(255), PRIMARY KEY (id))");
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO person(id, name) VALUES( ?, ?)")) {
            preparedStatement.setInt(1, 1);
            preparedStatement.setString(2, "Elly");
            int count = preparedStatement.executeUpdate();
            assertThat(count).isEqualTo(1);
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT id, name FROM person WHERE name = ?")) {
            preparedStatement.setString(1, "Elly");
            try (ResultSet rs = preparedStatement.executeQuery()) {
                assertThat(rs.next()).isEqualTo(true);
                assertThat(rs.getInt(1)).isEqualTo(1);
                assertThat(rs.getString(2)).isEqualTo("Elly");
                // No more records
                assertThat(rs.next()).isEqualTo(false);
            }
        }
    }
}
