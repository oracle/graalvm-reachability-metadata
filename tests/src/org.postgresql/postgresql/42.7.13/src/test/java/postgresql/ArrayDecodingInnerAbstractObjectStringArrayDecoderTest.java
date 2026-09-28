/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises string-decoded JDBC arrays backed by {@code ArrayDecoding.AbstractObjectStringArrayDecoder}.
 */
public class ArrayDecodingInnerAbstractObjectStringArrayDecoderTest {

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
            try (Connection connection = openConnection()) {
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
    void getArrayDecodesOneDimensionalNumericArray() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT ARRAY[1::numeric, NULL::numeric, 3::numeric]")) {
            assertThat(resultSet.next()).isTrue();

            Array sqlArray = resultSet.getArray(1);
            try {
                Object decodedArray = sqlArray.getArray();

                assertThat(decodedArray).isInstanceOf(BigDecimal[].class);
                BigDecimal[] values = (BigDecimal[]) decodedArray;
                assertThat(values).containsExactly(new BigDecimal("1"), null, new BigDecimal("3"));
            } finally {
                sqlArray.free();
            }
        }
    }

    @Test
    void getArrayDecodesMultiDimensionalNumericArray() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT ARRAY[[1::numeric, 2::numeric], [3::numeric, 4::numeric]]")) {
            assertThat(resultSet.next()).isTrue();

            Array sqlArray = resultSet.getArray(1);
            try {
                Object decodedArray = sqlArray.getArray();

                assertThat(decodedArray).isInstanceOf(BigDecimal[][].class);
                BigDecimal[][] values = (BigDecimal[][]) decodedArray;
                assertThat(values.length).isEqualTo(2);
                assertThat(values[0]).containsExactly(new BigDecimal("1"), new BigDecimal("2"));
                assertThat(values[1]).containsExactly(new BigDecimal("3"), new BigDecimal("4"));
            } finally {
                sqlArray.free();
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", USERNAME);
        properties.setProperty("password", PASSWORD);
        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + databasePort + "/" + DATABASE + "?preferQueryMode=simple",
                properties);
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
