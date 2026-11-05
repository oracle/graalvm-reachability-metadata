/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import oracle.jdbc.OracleDriver;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/// Shares one real database across the JDBC integration tests and stops it with the test suite.
public class OracleDatabase implements BeforeAllCallback {
    static final String USER = "metadata_user";
    static final String PASSWORD = "MetadataTest42";
    private static Database database;

    @Override
    public void beforeAll(ExtensionContext context) {
        database = context.getRoot().getStore(ExtensionContext.Namespace.create(OracleDatabase.class))
                .getOrComputeIfAbsent(Database.class, key -> new Database(), Database.class);
    }

    static String url() {
        return database.url;
    }

    static Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user", USER);
        properties.setProperty("password", PASSWORD);
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "30000");
        return properties;
    }

    static Connection openConnection() throws SQLException {
        return new OracleDriver().connect(url(), properties());
    }

    private static String docker(Duration timeout, String... arguments) throws IOException, InterruptedException {
        String[] command = new String[arguments.length + 1];
        command[0] = "docker";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Path output = Files.createTempFile("oracle-docker-", ".log");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IOException("Docker command timed out: " + String.join(" ", command));
                }
                String result = Files.readString(output);
                if (process.exitValue() != 0) {
                    throw new IOException("Docker command failed: " + result);
                }
                return result.trim();
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static final class Database implements ExtensionContext.Store.CloseableResource {
        private final String name = "ojdbc-metadata-" + UUID.randomUUID();
        private String url;

        private Database() {
            try {
                docker(Duration.ofMinutes(10), "run", "--detach", "--rm", "--name", name,
                        "--publish", "127.0.0.1::1521", "--env", "ORACLE_PASSWORD=" + PASSWORD,
                        "--env", "APP_USER=" + USER, "--env", "APP_USER_PASSWORD=" + PASSWORD,
                        "gvenzl/oracle-free:23-slim-faststart");
                String address = docker(Duration.ofSeconds(30), "port", name, "1521/tcp");
                url = "jdbc:oracle:thin:@//" + address + "/FREEPDB1";
                long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
                SQLException lastFailure = null;
                while (System.nanoTime() < deadline) {
                    try (Connection connection = new OracleDriver().connect(url, properties())) {
                        if (connection.isValid(10)) {
                            return;
                        }
                    } catch (SQLException exception) {
                        lastFailure = exception;
                    }
                    Thread.sleep(1000);
                }
                throw new SQLException("Oracle database did not become ready", lastFailure);
            } catch (Exception exception) {
                try {
                    close();
                } catch (Exception cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                throw new IllegalStateException("Cannot start Oracle test database", exception);
            }
        }

        @Override
        public void close() throws IOException, InterruptedException {
            docker(Duration.ofSeconds(60), "rm", "--force", name);
        }
    }
}
