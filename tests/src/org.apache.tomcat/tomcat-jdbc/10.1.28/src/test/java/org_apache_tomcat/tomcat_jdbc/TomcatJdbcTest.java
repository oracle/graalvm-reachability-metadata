/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatJdbcTest {

    @Test
    void retrievesDataUsingTheDefaultStatementFacade() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("driverClassName", "org.h2.Driver");
        properties.setProperty("url", "jdbc:h2:mem:tomcat_jdbc;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        properties.setProperty("username", "sa");
        properties.setProperty("password", "");

        PoolConfiguration poolConfiguration = DataSourceFactory.parsePoolProperties(properties);
        assertThat(poolConfiguration.getUseStatementFacade()).isTrue();

        DataSource dataSource = new DataSource(poolConfiguration);
        try {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE item (id INT PRIMARY KEY, name VARCHAR(255))");
                statement.execute("INSERT INTO item(id, name) VALUES (1, 'pooled')");

                try (ResultSet resultSet = statement.executeQuery("SELECT name FROM item WHERE id = 1")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("name")).isEqualTo("pooled");
                }
            }
        } finally {
            dataSource.close();
        }
    }
}
