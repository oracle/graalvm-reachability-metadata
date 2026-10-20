/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.dynamic_datasource_creator;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.basic.BasicDataSourceCreator;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

public class BasicDataSourceCreatorTest {
    @Test
    void createsConfiguredHikariDatasourceThroughSpringBootBuilder() throws Exception {
        DataSourceProperty property = new DataSourceProperty();
        property.setPoolName("basic-hikari");
        property.setType(HikariDataSource.class);
        property.setDriverClassName("org.h2.Driver");
        property.setUrl("jdbc:h2:mem:basic_creator;DB_CLOSE_DELAY=-1");
        property.setUsername("sa");
        property.setPassword("");

        BasicDataSourceCreator creator = new BasicDataSourceCreator();
        DataSource dataSource = creator.createDataSource(property);

        assertThat(creator.support(property)).isTrue();
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        try (HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                Connection connection = hikariDataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 42")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(42);
            assertThat(resultSet.next()).isFalse();
        }
    }
}
