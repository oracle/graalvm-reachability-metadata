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

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatJdbcTest {

    @Test
    void createsConfiguredDataSource() {
        PoolConfiguration poolConfiguration = DataSourceFactory.parsePoolProperties(createProperties());
        DataSource dataSource = new DataSource(poolConfiguration);

        assertThat(dataSource.getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(dataSource.getUrl()).isEqualTo("jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        assertThat(dataSource.getUsername()).isEqualTo("fred");
        assertThat(dataSource.getInitialSize()).isEqualTo(2);
        assertThat(dataSource.getMinIdle()).isEqualTo(1);
        assertThat(dataSource.isTestOnBorrow()).isTrue();
        assertThat(dataSource.isTestOnConnect()).isTrue();
        assertThat(dataSource.getValidationQuery()).isEqualTo("select 1");
    }

    private Properties createProperties() {
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
        return properties;
    }
}
