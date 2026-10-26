/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_mysql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;

/// A database server running in a throw-away Docker container on a random local port.
public final class DockerDatabaseServer implements AutoCloseable {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(50);

    private final String containerName;

    private final String jdbcUrlPrefix;

    private final String user;

    private final String password;

    private DockerDatabaseServer(String containerName, String jdbcUrlPrefix, String user, String password) {
        this.containerName = containerName;
        this.jdbcUrlPrefix = jdbcUrlPrefix;
        this.user = user;
        this.password = password;
    }

    /// Starts `image`, publishes its port 3306 and waits until JDBC connections to `jdbcScheme` succeed.
    public static DockerDatabaseServer start(String image, String jdbcScheme, String password, List<String> environment)
            throws IOException, InterruptedException {
        String containerName = "flyway-mysql-tests-" + UUID.randomUUID();
        List<String> command = new ArrayList<>(List.of("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", "127.0.0.1::3306"));
        for (String variable : environment) {
            command.add("-e");
            command.add(variable);
        }
        command.add(image);
        run(command);
        DockerDatabaseServer server;
        try {
            String mapping = run(List.of("docker", "port", containerName, "3306/tcp")).lines()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No port mapping for " + containerName));
            int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1).trim());
            server = new DockerDatabaseServer(containerName, jdbcScheme + "//127.0.0.1:" + port + "/", "root",
                    password);
            server.awaitConnections();
        } catch (RuntimeException | IOException | InterruptedException e) {
            run(List.of("docker", "rm", "-f", containerName));
            throw e;
        }
        return server;
    }

    /// Returns a JDBC URL for `database`, or for the server without a default database when it is empty.
    public String jdbcUrl(String database) {
        return jdbcUrlPrefix + database;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    /// Opens a plain JDBC connection that does not go through Flyway.
    public Connection openConnection(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), user, password);
    }

    private void awaitConnections() {
        Awaitility.await()
                .atMost(STARTUP_TIMEOUT)
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions()
                .until(() -> {
                    try (Connection connection = openConnection("")) {
                        return connection.isValid(10);
                    }
                });
    }

    @Override
    public void close() throws IOException, InterruptedException {
        run(List.of("docker", "rm", "-f", containerName));
    }

    private static String run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out running " + command);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Command " + command + " failed: " + output);
        }
        return output;
    }
}
