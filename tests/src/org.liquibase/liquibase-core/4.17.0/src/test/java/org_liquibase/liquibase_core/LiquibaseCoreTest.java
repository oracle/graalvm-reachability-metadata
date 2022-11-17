/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

class LiquibaseCoreTest {

    @Test
    void testYaml() throws Exception {
        final String jdbcUrl = "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1";
        withConnection(jdbcUrl, (connection) -> {
            Database database = new H2Database();
            database.setConnection(new JdbcConnection(connection));

            Liquibase liquibase = new Liquibase("changelog.yaml", new ClassLoaderResourceAccessor(), database);
            liquibase.update();
        });

        withConnection(jdbcUrl, (connection) -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO state (id) VALUES (?)");
            statement.setString(1, "CO");
            statement.execute();

            statement = connection.prepareStatement("INSERT INTO person (firstname, lastname, state, username) VALUES (?, ?, ?, ?)");
            statement.setString(1, "first-1");
            statement.setString(2, "last-1");
            statement.setString(3, "CO");
            statement.setString(4, "user-1");
            statement.execute();
        });
    }

    @Test
    void fullChangelogTest() throws Exception {
        final String jdbcUrl = "jdbc:h2:mem:db2;DB_CLOSE_DELAY=-1";
        withConnection(jdbcUrl, (connection) -> {
            Database database = new H2Database();
            database.setConnection(new JdbcConnection(connection));

            Liquibase liquibase = new Liquibase("changelog.xml", new ClassLoaderResourceAccessor(), database);
            liquibase.update();
        });

        withConnection(jdbcUrl, (connection) -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO person (fullname, employer_id) VALUES (?, ?)");
            statement.setString(1, "first-1 last-1");
            statement.setInt(2, 10);
            statement.execute();
        });
    }

    private static void withConnection(String url, ConnectionCallback callback) throws Exception {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            callback.run(connection);
        }
    }

    private interface ConnectionCallback {
        void run(Connection connection) throws Exception;
    }
}
