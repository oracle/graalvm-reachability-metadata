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
import org.postgresql.core.BaseConnection;
import org.postgresql.geometric.PGpoint;
import org.postgresql.util.PSQLException;
import org.postgresql.xml.PGXmlFactoryFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.jdbc.PgConnection}.
 */
public class PgConnectionTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:15436/" + DATABASE;

    private static Process process;

    private static Connection openConnection() throws SQLException {
        return openConnection(new Properties());
    }

    private static Connection openConnection(Properties additionalProperties) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", USERNAME);
        properties.setProperty("password", PASSWORD);
        properties.putAll(additionalProperties);
        return DriverManager.getConnection(JDBC_URL, properties);
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting OpenGauss for PgConnection coverage ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "15436:5432", "-e", "GS_USERNAME=" + USERNAME,
                "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-pgconnection-stdout.txt"))
                .redirectError(new File("opengauss-pgconnection-stderr.txt"))
                .start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            openConnection().close();
            return true;
        });
        System.out.println("OpenGauss started for PgConnection coverage");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down OpenGauss for PgConnection coverage");
            process.destroy();
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void addDataTypeLoadsPgObjectClassByName() throws Exception {
        try (Connection connection = openConnection()) {
            PGConnection pgConnection = connection.unwrap(PGConnection.class);
            BaseConnection baseConnection = connection.unwrap(BaseConnection.class);

            pgConnection.addDataType("runtime_point", PGpoint.class.getName());

            assertThat(baseConnection.getTypeInfo().getPGobject("runtime_point")).isEqualTo(PGpoint.class);
        }
    }

    @Test
    void initObjectTypesLoadsConfiguredPgObjectClassByName() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("datatype.startup_point", PGpoint.class.getName());

        try (Connection connection = openConnection(properties)) {
            BaseConnection baseConnection = connection.unwrap(BaseConnection.class);

            assertThat(baseConnection.getTypeInfo().getPGobject("startup_point")).isEqualTo(PGpoint.class);
        }
    }

    @Test
    void getXmlFactoryFactoryLoadsConfiguredClassByNameBeforeInstantiationFails() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("xmlFactoryFactory", PGXmlFactoryFactory.class.getName());

        try (Connection connection = openConnection(properties)) {
            BaseConnection baseConnection = connection.unwrap(BaseConnection.class);

            assertThatThrownBy(baseConnection::getXmlFactoryFactory)
                    .isInstanceOf(PSQLException.class)
                    .hasMessageContaining("Could not instantiate xmlFactoryFactory");
        }
    }
}
