/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TomcatJdbcTest {

    private DataSource dataSource;
    private Connection connection;

    @BeforeAll
    public void init() throws Exception {
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
        DataSourceFactory dataSourceFactory = new DataSourceFactory();
        dataSource = dataSourceFactory.createDataSource(properties);
    }

    @AfterAll
    public void close() throws Exception {
        ((org.apache.tomcat.jdbc.pool.DataSource) dataSource).close(true);
    }

    @BeforeEach
    public void setConnection() throws SQLException {
        connection = dataSource.getConnection();
    }

    @AfterEach
    public void removeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    @Test
    void testPreparedStatement() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE foo (id INT NOT NULL, name VARCHAR(255), PRIMARY KEY (id))");
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO foo(id, name) VALUES( ?, ?)")) {
            preparedStatement.setInt(1, 1);
            preparedStatement.setString(2, "test1");
            int count = preparedStatement.executeUpdate();
            assertThat(count).isEqualTo(1);
        }
    }

    @Test
    void testCallableStatement() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE ALIAS my_round FOR 'java.lang.Math.round(double)'");
        }
        try (CallableStatement callableStatement = connection.prepareCall("CALL my_round(?)")) {
            callableStatement.setFloat(1, 4567.9874f);
            ResultSet resultSet = callableStatement.executeQuery();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(4568);
        }
    }
}
