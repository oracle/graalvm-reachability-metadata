/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

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

final class OracleTestDatabase implements AutoCloseable {
    private static final String PASSWORD = "Jdbc_Test42";
    private final String name = "spring-jdbc-oracle-" + UUID.randomUUID();
    private String url;

    static OracleTestDatabase start() throws Exception {
        OracleTestDatabase database = new OracleTestDatabase();
        try {
            docker("run", "--detach", "--rm", "--pull=never", "--name", database.name,
                    "--publish", "127.0.0.1::1521", "--env", "ORACLE_PASSWORD=" + PASSWORD,
                    "--env", "APP_USER=spring_jdbc", "--env", "APP_USER_PASSWORD=" + PASSWORD,
                    "gvenzl/oracle-free:23-slim-faststart");
            database.url = "jdbc:oracle:thin:@//" + docker("port", database.name, "1521/tcp").trim() + "/FREEPDB1";
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            while (System.nanoTime() < deadline) {
                if (docker("logs", database.name).contains("DATABASE IS READY TO USE!")) {
                    return database;
                }
                Thread.sleep(1000);
            }
            throw new IOException("Oracle database did not become ready: " + docker("logs", database.name));
        } catch (Exception failure) {
            try {
                database.close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    Connection openConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", "spring_jdbc");
        properties.setProperty("password", PASSWORD);
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "15000");
        return new OracleDriver().connect(url, properties);
    }

    @Override
    public void close() throws Exception {
        docker("rm", "--force", name);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(Arrays.asList(arguments));
        Path output = Files.createTempFile("spring-jdbc-oracle-", ".log");
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
                return result;
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
}
