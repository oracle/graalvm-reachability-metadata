/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.hsqldb.Database;
import org.hsqldb.DatabaseManager;
import org.hsqldb.DatabaseURL;
import org.hsqldb.Session;
import org.hsqldb.SessionInterface;
import org.hsqldb.dbinfo.DatabaseInformation;
import org.hsqldb.jdbc.JDBCConnection;
import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.persist.HsqlProperties;
import org.junit.jupiter.api.Test;

public class DatabaseInformationTest {
    private static final String FULL_DATABASE_INFORMATION_CLASS =
            "org.hsqldb.dbinfo.DatabaseInformationFull";

    @Test
    void exposesInformationSchemaThroughJdbcMetadata() throws SQLException {
        try (Connection connection = openConnection()) {
            assertThat(connection.isValid(10)).isTrue();

            DatabaseMetaData metadata = connection.getMetaData();

            assertThat(metadata.getDatabaseProductName()).containsIgnoringCase("HSQL");
            assertInformationSchemaTableIsVisible(metadata);
            assertSystemTablesCanBeQueried(connection);
            assertDatabaseInformationFactoryCreatesMetadataProducer(connection);
        }
    }

    @Test
    void createsFullDatabaseInformationProducerFromCoreDatabaseApi() {
        String databaseName = uniqueDatabaseName();
        Database database = DatabaseManager.getDatabase(
                DatabaseURL.S_MEM,
                databaseName,
                new HsqlProperties());

        try {
            Session session = database.getSessionManager().getSysSession();

            assertFullDatabaseInformation(database, session);
        } finally {
            database.close(Database.CLOSEMODE_NORMAL);
        }
    }

    private static Connection openConnection() throws SQLException {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl("jdbc:hsqldb:mem:" + uniqueDatabaseName() + ";shutdown=true");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static String uniqueDatabaseName() {
        String uuid = UUID.randomUUID().toString().replace("-", "");

        return "DatabaseInformationTest" + uuid;
    }

    private static void assertInformationSchemaTableIsVisible(
            DatabaseMetaData metadata) throws SQLException {
        try (ResultSet tables = metadata.getTables(
                null,
                "INFORMATION_SCHEMA",
                "SYSTEM_TABLES",
                null)) {
            assertThat(tables.next()).isTrue();
            assertThat(tables.getString("TABLE_SCHEM")).isEqualTo("INFORMATION_SCHEMA");
            assertThat(tables.getString("TABLE_NAME")).isEqualTo("SYSTEM_TABLES");
        }
    }

    private static void assertSystemTablesCanBeQueried(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT TABLE_NAME
                        FROM INFORMATION_SCHEMA.SYSTEM_TABLES
                        WHERE TABLE_SCHEM = 'INFORMATION_SCHEMA'
                          AND TABLE_NAME = 'SYSTEM_TABLES'
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("TABLE_NAME")).isEqualTo("SYSTEM_TABLES");
        }
    }

    private static void assertDatabaseInformationFactoryCreatesMetadataProducer(
            Connection connection) {
        JDBCConnection jdbcConnection = (JDBCConnection) connection;
        SessionInterface sessionInterface = jdbcConnection.getSession();

        assertThat(sessionInterface).isInstanceOf(Session.class);

        Session session = (Session) sessionInterface;

        assertFullDatabaseInformation(session.getDatabase(), session);
    }

    private static void assertFullDatabaseInformation(Database database, Session session) {
        DatabaseInformation databaseInformation = DatabaseInformation
                .newDatabaseInformation(database);

        assertThat(databaseInformation).isInstanceOf(DatabaseInformation.class);
        assertThat(databaseInformation.getClass().getName())
                .isEqualTo(FULL_DATABASE_INFORMATION_CLASS);
        assertThat(databaseInformation.getSystemTable(session, "SYSTEM_TABLES")).isNotNull();
    }
}
