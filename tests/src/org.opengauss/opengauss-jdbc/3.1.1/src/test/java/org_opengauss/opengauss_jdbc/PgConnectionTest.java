/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.util.PGInterval;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.time.Duration;
import java.util.Properties;

import javax.xml.transform.sax.SAXResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PgConnectionTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:15435/" + DATABASE;
    private static Process process;

    private static Connection openConnection() throws SQLException {
        return openConnection(new Properties());
    }

    private static Connection openConnection(Properties properties) throws SQLException {
        properties.setProperty("user", USERNAME);
        properties.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, properties);
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting OpenGauss for PgConnection tests ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "15435:5432", "-e", "GS_USERNAME=" + USERNAME,
                "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-pg-connection-stdout.txt"))
                .redirectError(new File("opengauss-pg-connection-stderr.txt")).start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            openConnection().close();
            return true;
        });
        System.out.println("OpenGauss for PgConnection tests started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down OpenGauss for PgConnection tests");
            process.destroy();
        }
    }

    @Test
    void registersCustomDataTypesFromConnectionPropertiesAndPublicApi() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("datatype.pg_connection_custom", PGInterval.class.getName());

        try (Connection connection = openConnection(properties)) {
            assertThat(connection.isClosed()).isFalse();

            PGConnection pgConnection = connection.unwrap(PGConnection.class);
            pgConnection.addDataType("pg_connection_custom_api", PGInterval.class.getName());
        }
    }

    @Test
    void rejectsNonXmlFactoryClassThroughSqlxmlApi() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("xmlFactoryFactory", Object.class.getName());

        try (Connection connection = openConnection(properties)) {
            SQLXML sqlxml = connection.createSQLXML();

            assertThatThrownBy(() -> sqlxml.setResult(SAXResult.class))
                    .isInstanceOf(SQLException.class);
        }
    }
}
