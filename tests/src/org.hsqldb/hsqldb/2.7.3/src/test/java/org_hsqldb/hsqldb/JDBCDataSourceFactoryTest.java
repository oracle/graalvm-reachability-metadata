/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import javax.naming.Reference;
import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.jdbc.JDBCDataSourceFactory;
import org.junit.jupiter.api.Test;

public class JDBCDataSourceFactoryTest {
    @Test
    void createsDataSourceFromProperties() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("database", "jdbc:hsqldb:mem:data_source_factory_properties");
        properties.setProperty("user", "SA");
        properties.setProperty("password", "");
        properties.setProperty("loginTimeout", "10");

        DataSource dataSource = JDBCDataSourceFactory.createDataSource(properties);

        assertThat(dataSource).isInstanceOf(JDBCDataSource.class);
        JDBCDataSource jdbcDataSource = (JDBCDataSource) dataSource;
        assertThat(jdbcDataSource.getDatabase())
                .isEqualTo("jdbc:hsqldb:mem:data_source_factory_properties");
        assertThat(jdbcDataSource.getUser()).isEqualTo("SA");
        assertThat(jdbcDataSource.getLoginTimeout()).isEqualTo(10);
        assertConnectionIsUsable(dataSource);
    }

    @Test
    void createsDataSourceFromJndiReference() throws Exception {
        JDBCDataSource original = new JDBCDataSource();
        original.setDatabase("jdbc:hsqldb:mem:data_source_factory_reference");
        original.setUser("SA");
        original.setPassword("");
        original.setLoginTimeout(10);
        Reference reference = original.getReference();

        Object object = new JDBCDataSourceFactory().getObjectInstance(
                reference, null, null, null);

        assertThat(object).isInstanceOf(JDBCDataSource.class);
        JDBCDataSource reconstructed = (JDBCDataSource) object;
        assertThat(reconstructed.getDatabase())
                .isEqualTo("jdbc:hsqldb:mem:data_source_factory_reference");
        assertThat(reconstructed.getUser()).isEqualTo("SA");
        assertThat(reconstructed.getLoginTimeout()).isEqualTo(10);
        assertConnectionIsUsable(reconstructed);
    }

    private static void assertConnectionIsUsable(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("VALUES 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }
}
