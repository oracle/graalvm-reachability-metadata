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
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

import org.awaitility.Awaitility;

/**
 * Starts an isolated PostgreSQL container on a dynamically assigned localhost port.
 */
final class PostgresqlTestContainer implements AutoCloseable {

    private static final String HOST = "127.0.0.1";

    private static final int MAX_START_ATTEMPTS = 3;

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(1);

    private final String database;

    private final String username;

    private final String password;

    private String containerId;

    private final int databasePort;

    private PostgresqlTestContainer(String database, String username, String password, String containerId, int databasePort) {
        this.database = database;
        this.username = username;
        this.password = password;
        this.containerId = containerId;
        this.databasePort = databasePort;
    }

    static PostgresqlTestContainer start(String database, String username, String password) throws Exception {
        for (int attempt = 1; attempt <= MAX_START_ATTEMPTS; attempt++) {
            try {
                String containerId = commandOutput("docker", "run", "--rm", "-d", "-p", HOST + "::5432", "-e",
                        "POSTGRES_DB=" + database, "-e", "POSTGRES_USER=" + username, "-e", "POSTGRES_PASSWORD=" + password,
                        "postgres:18-alpine");
                int databasePort = Integer.parseInt(commandOutput("docker", "inspect", "--format",
                        "{{(index (index .NetworkSettings.Ports \"5432/tcp\") 0).HostPort}}", containerId));
                PostgresqlTestContainer container = new PostgresqlTestContainer(database, username, password, containerId,
                        databasePort);
                try {
                    container.awaitUntilReady();
                    return container;
                } catch (Throwable throwable) {
                    container.close();
                    throw throwable;
                }
            } catch (IllegalStateException exception) {
                if (!isRetryableStartupFailure(exception) || attempt == MAX_START_ATTEMPTS) {
                    throw exception;
                }
                Thread.sleep(Duration.ofSeconds(attempt).toMillis());
            }
        }
        throw new IllegalStateException("PostgreSQL container did not start");
    }

    String host() {
        return HOST;
    }

    int port() {
        return databasePort;
    }

    String jdbcUrl() {
        return "jdbc:postgresql://" + host() + ":" + port() + "/" + database;
    }

    Properties connectionProperties() {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        return properties;
    }

    Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), connectionProperties());
    }

    @Override
    public void close() throws Exception {
        if (containerId != null) {
            try {
                commandOutput("docker", "rm", "-f", containerId);
            } finally {
                containerId = null;
            }
        }
    }

    private void awaitUntilReady() {
        Awaitility.await().atMost(STARTUP_TIMEOUT).ignoreExceptions().until(() -> {
            try (Connection connection = openConnection()) {
                return connection.isValid(1);
            }
        });
    }

    private static boolean isRetryableStartupFailure(IllegalStateException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("bind: address already in use")
                || message.contains("port is already allocated"));
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
