/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import oracle.jdbc.OracleDriver;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/// Shares a real database across integration tests and closes it with the suite.
public class OracleDatabase implements BeforeAllCallback, BeforeEachCallback {
    private static final String USER = "metadata_user";
    private static final String PASSWORD = "MetadataTest42";
    private static Database database;

    @Override
    public void beforeAll(ExtensionContext context) {
        initialize(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        initialize(context);
    }

    private static void initialize(ExtensionContext context) {
        database = context.getRoot().getStore(ExtensionContext.Namespace.create(OracleDatabase.class))
                .getOrComputeIfAbsent(Database.class, key -> new Database(), Database.class);
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
        return openConnection(properties());
    }

    static Connection openConnection(Properties properties) throws SQLException {
        return new OracleDriver().connect(database.url, properties);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(Arrays.asList(arguments));
        Path output = Files.createTempFile("ojdbc-docker-", ".log");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            try {
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    throw new IOException("Docker command timed out: " + command);
                }
                String result = Files.readString(output);
                if (process.exitValue() != 0) {
                    throw new IOException("Docker command failed: " + result);
                }
                return result.trim();
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        throw new IOException("Docker command did not terminate: " + command);
                    }
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
                docker("run", "--detach", "--rm", "--pull=never", "--name", name,
                        "--publish", "127.0.0.1::1521", "--env", "ORACLE_PASSWORD=" + PASSWORD,
                        "--env", "APP_USER=" + USER, "--env", "APP_USER_PASSWORD=" + PASSWORD,
                        "gvenzl/oracle-free:23-slim-faststart");
                url = "jdbc:oracle:thin:@//" + docker("port", name, "1521/tcp") + "/FREEPDB1";
                long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
                while (System.nanoTime() < deadline) {
                    if (docker("logs", name).contains("DATABASE IS READY TO USE!")) {
                        return;
                    }
                    Thread.sleep(1000);
                }
                throw new IOException("Oracle database did not become ready: " + docker("logs", name));
            } catch (Exception failure) {
                try {
                    close();
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw new IllegalStateException("Cannot start Oracle test database", failure);
            }
        }

        @Override
        public void close() throws Exception {
            docker("rm", "--force", name);
        }
    }
}
