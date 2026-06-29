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
import javax.naming.spi.NamingManager;
import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.jdbc.JDBCDataSourceFactory;
import org.junit.jupiter.api.Test;

public class JDBCDataSourceFactoryTest {
    @Test
    void createsDataSourceFromProperties() throws Exception {
        String databaseUrl = inMemoryDatabaseUrl();
        Properties properties = new Properties();

        properties.setProperty("database", databaseUrl);
        properties.setProperty("user", "SA");
        properties.setProperty("password", "");
        properties.setProperty("loginTimeout", "10");

        DataSource dataSource = JDBCDataSourceFactory.createDataSource(properties);

        assertThat(dataSource).isInstanceOf(JDBCDataSource.class);
        JDBCDataSource jdbcDataSource = (JDBCDataSource) dataSource;
        assertThat(jdbcDataSource.getDatabase()).isEqualTo(databaseUrl);
        assertThat(jdbcDataSource.getUser()).isEqualTo("SA");
        assertThat(jdbcDataSource.getLoginTimeout()).isEqualTo(10);
        assertCanUseDataSource(dataSource);
    }

    @Test
    void createsDataSourceFromUrlAndUsernameAliases() throws Exception {
        String databaseUrl = inMemoryDatabaseUrl();
        Properties properties = new Properties();

        properties.setProperty("url", databaseUrl);
        properties.setProperty("username", "SA");
        properties.setProperty("password", "");
        properties.setProperty("loginTimeout", " 10 ");

        DataSource dataSource = JDBCDataSourceFactory.createDataSource(properties);

        assertCreatedDataSource(dataSource, databaseUrl);
    }

    @Test
    void createsDataSourceFromJndiReference() throws Exception {
        String databaseUrl = inMemoryDatabaseUrl();
        Reference reference = createReference(databaseUrl);
        JDBCDataSourceFactory factory = new JDBCDataSourceFactory();

        Object object = factory.getObjectInstance(reference, null, null, null);

        assertCreatedDataSource(object, databaseUrl);
    }

    @Test
    void createsDataSourceThroughJndiNamingManager() throws Exception {
        String databaseUrl = inMemoryDatabaseUrl();
        Reference reference = createReference(databaseUrl);

        Object object = NamingManager.getObjectInstance(reference, null, null, null);

        assertCreatedDataSource(object, databaseUrl);
    }

    private static Reference createReference(String databaseUrl) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setDatabase(databaseUrl);
        dataSource.setUser("SA");
        dataSource.setPassword("");
        dataSource.setLoginTimeout(10);

        return dataSource.getReference();
    }

    private static void assertCreatedDataSource(
            Object object,
            String databaseUrl) throws Exception {
        assertThat(object).isInstanceOf(JDBCDataSource.class);
        JDBCDataSource jdbcDataSource = (JDBCDataSource) object;
        assertThat(jdbcDataSource.getDatabase()).isEqualTo(databaseUrl);
        assertThat(jdbcDataSource.getUser()).isEqualTo("SA");
        assertThat(jdbcDataSource.getLoginTimeout()).isEqualTo(10);
        assertCanUseDataSource(jdbcDataSource);
    }

    private static void assertCanUseDataSource(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE factory_items(
                        id INTEGER PRIMARY KEY,
                        name VARCHAR(20))
                    """);
            statement.execute("INSERT INTO factory_items VALUES (1, 'created')");

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT name FROM factory_items WHERE id = 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("created");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    private static String inMemoryDatabaseUrl() {
        return "jdbc:hsqldb:mem:factory_" + System.nanoTime() + ";shutdown=true";
    }
}
