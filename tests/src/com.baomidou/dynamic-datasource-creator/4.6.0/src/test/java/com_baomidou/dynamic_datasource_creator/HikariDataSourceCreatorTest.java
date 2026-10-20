/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.dynamic_datasource_creator;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.hikaricp.HikariCpConfig;
import com.baomidou.dynamic.datasource.creator.hikaricp.HikariDataSourceCreator;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

public class HikariDataSourceCreatorTest {
    @Test
    void createsLazyDatasourceAndConnectsWithCopiedConfiguration() throws Exception {
        HikariCpConfig global = new HikariCpConfig();
        global.setMaxPoolSize(2);
        global.setMinIdle(0);

        DataSourceProperty property = new DataSourceProperty();
        property.setPoolName("lazy-hikari");
        property.setDriverClassName("org.h2.Driver");
        property.setUrl("jdbc:h2:mem:lazy_hikari_creator;DB_CLOSE_DELAY=-1");
        property.setUsername("sa");
        property.setPassword("");
        property.setLazy(true);

        HikariDataSourceCreator creator = new HikariDataSourceCreator(global);
        DataSource created = creator.createDataSource(property);

        assertThat(creator.support(property)).isTrue();
        assertThat(created).isInstanceOf(HikariDataSource.class);
        try (HikariDataSource dataSource = (HikariDataSource) created;
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 42")) {
            assertThat(dataSource.getPoolName()).isEqualTo("lazy-hikari");
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(2);
            assertThat(dataSource.getMinimumIdle()).isZero();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(42);
            assertThat(resultSet.next()).isFalse();
        }
    }
}
