/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oceanbase.oceanbase_client;

import com.oceanbase.jdbc.Driver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Timeout(170)
public abstract class DatabaseTestSupport {
    protected static MySqlServer database;

    @BeforeAll
    static void startDatabase() throws Exception {
        database = new MySqlServer();
        try {
            database.start();
        } catch (Exception | Error failure) {
            database.close();
            throw failure;
        }
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (database != null) {
            database.close();
        }
    }

    protected static final class MySqlServer implements AutoCloseable {
        private static final String IMAGE = "docker.io/library/mariadb:13.0";
        private final String name = "oceanbase-test-" + UUID.randomUUID();
        private final Path directory;
        private int port;
        private boolean created;

        MySqlServer() throws IOException {
            directory = Files.createTempDirectory("oceanbase-database-");
        }

        void start() throws Exception {
            command("docker", "create", "--name", name, "-p", "127.0.0.1::3306",
                    "-e", "MARIADB_ROOT_PASSWORD=metadata-secret", "-e", "MARIADB_ROOT_HOST=%",
                    "-e", "MARIADB_DATABASE=metadata_test", IMAGE,
                    "--performance-schema=ON", "--performance-schema-session-connect-attrs-size=4096",
                    "--ssl-ca=/tmp/server.pem", "--ssl-cert=/tmp/server.pem", "--ssl-key=/tmp/server-key.pem");
            created = true;
            copyTlsFixture("server.pem",
                    DatabaseTestSupport.class.getResourceAsStream("/oceanbase-tls/server.pem"));
            copyTlsFixture("server-key.pem",
                    DatabaseTestSupport.class.getResourceAsStream("/oceanbase-tls/server-key.pem"));
            command("docker", "start", name);
            String mapping = command("docker", "port", name, "3306/tcp").lines()
                    .filter(line -> line.startsWith("127.0.0.1:")).findFirst().orElseThrow();
            port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            SQLException lastFailure = null;
            while (System.nanoTime() < deadline) {
                try (Connection connection = connect("", new Properties())) {
                    if (connection.isValid(10)) {
                        return;
                    }
                } catch (SQLException failure) {
                    Throwable cause = failure;
                    while (cause != null && !(cause instanceof IOException)) {
                        cause = cause.getCause();
                    }
                    if (cause == null) {
                        throw failure;
                    }
                    lastFailure = failure;
                }
                Thread.sleep(1000);
            }
            throw new SQLException("MySQL did not become ready: " + command("docker", "logs", name), lastFailure);
        }

        private void copyTlsFixture(String file, InputStream fixture) throws Exception {
            Path destination = directory.resolve(file);
            try (InputStream input = fixture) {
                if (input == null) {
                    throw new IOException("Missing TLS fixture: " + file);
                }
                Files.copy(input, destination);
            }
            command("docker", "cp", destination.toString(), name + ":/tmp/" + file);
        }

        Connection connect(String mode, Properties overrides) throws SQLException {
            Properties properties = new Properties();
            properties.setProperty("user", "root");
            properties.setProperty("password", "metadata-secret");
            properties.setProperty("connectTimeout", "10000");
            properties.setProperty("socketTimeout", "30000");
            properties.setProperty("useSsl", "false");
            properties.setProperty("allowPublicKeyRetrieval", "true");
            properties.setProperty("useOceanBaseProtocolV20", "false");
            properties.setProperty("retriesAllDown", "1");
            properties.setProperty("validConnectionTimeout", "0");
            properties.putAll(overrides);
            String hosts = "127.0.0.1:" + port;
            if ("replication".equals(mode)) {
                hosts = "address=(host=127.0.0.1)(port=" + port + ")(type=master),"
                        + "address=(host=127.0.0.1)(port=" + port + ")(type=slave)";
            }
            String prefix = mode.isEmpty() ? "" : mode + ":";
            return new Driver().connect("jdbc:oceanbase:" + prefix + "//" + hosts + "/metadata_test", properties);
        }

        private String command(String... arguments) throws Exception {
            Path output = Files.createTempFile(directory, "docker-", ".log");
            Process process = new ProcessBuilder(arguments).redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            try {
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    throw new IOException("Docker command timed out: " + String.join(" ", arguments));
                }
                String result = Files.readString(output);
                if (process.exitValue() != 0) {
                    throw new IOException("Docker command failed: " + String.join(" ", arguments) + "\n" + result);
                }
                return result.trim();
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            }
        }

        @Override
        public void close() throws Exception {
            if (created) {
                command("docker", "rm", "--force", "--volumes", name);
                created = false;
            }
            if (Files.exists(directory)) {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
        }
    }
}
