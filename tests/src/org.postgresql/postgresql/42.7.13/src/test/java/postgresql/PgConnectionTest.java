/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Properties;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGProperty;
import org.postgresql.core.BaseConnection;
import org.postgresql.util.PGInterval;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.xml.PGXmlFactoryFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.jdbc.PgConnection}.
 */
public class PgConnectionTest {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static String containerId;

    private static int databasePort;

    @BeforeAll
    static void beforeAll() throws Exception {
        containerId = commandOutput("docker", "run", "--rm", "-d", "-p", "127.0.0.1::5432", "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USERNAME, "-e", "POSTGRES_PASSWORD=" + PASSWORD, "postgres:18-alpine");
        databasePort = Integer.parseInt(commandOutput("docker", "inspect", "--format",
                "{{(index (index .NetworkSettings.Ports \"5432/tcp\") 0).HostPort}}", containerId));

        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            try (Connection connection = openConnection(newConnectionProperties())) {
                return connection.isValid(1);
            }
        });
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (containerId != null) {
            commandOutput("docker", "rm", "-f", containerId);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void addDataTypeLoadsClassNameThroughConnectionApi() throws Exception {
        try (Connection connection = openConnection(newConnectionProperties())) {
            PGConnection pgConnection = connection.unwrap(PGConnection.class);

            assertThatCode(() -> pgConnection.addDataType("interval", PGInterval.class.getName()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void constructorLoadsDatatypePropertiesDuringObjectTypeInitialization() throws Exception {
        Properties properties = newConnectionProperties();
        properties.setProperty("datatype.interval", PGInterval.class.getName());

        try (Connection connection = openConnection(properties)) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Test
    void customXmlFactoryFactoryPropertyUsesConfiguredClassName() throws Exception {
        Properties properties = newConnectionProperties();
        PGProperty.XML_FACTORY_FACTORY.set(properties, PGXmlFactoryFactory.class.getName());

        try (Connection connection = openConnection(properties)) {
            BaseConnection baseConnection = connection.unwrap(BaseConnection.class);

            PSQLException exception = catchThrowableOfType(baseConnection::getXmlFactoryFactory, PSQLException.class);

            assertThat((Throwable) exception).isNotNull();
            assertThat(exception.getSQLState()).isEqualTo(PSQLState.INVALID_PARAMETER_VALUE.getState());
        }
    }

    private static Connection openConnection(Properties properties) throws Exception {
        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + databasePort + "/" + DATABASE, properties);
    }

    private static Properties newConnectionProperties() {
        Properties properties = new Properties();
        properties.setProperty("user", USERNAME);
        properties.setProperty("password", PASSWORD);
        return properties;
    }

    private static String commandOutput(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        String text = new String(output, StandardCharsets.UTF_8).trim();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Command failed with exit code " + exitCode + ": " + String.join(" ", command) + "\n" + text);
        }
        return text;
    }
}
