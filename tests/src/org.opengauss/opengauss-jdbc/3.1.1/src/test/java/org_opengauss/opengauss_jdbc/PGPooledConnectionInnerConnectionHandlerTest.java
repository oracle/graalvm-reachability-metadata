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
import org.postgresql.PGStatement;
import org.postgresql.ds.PGPooledConnection;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PGPooledConnectionInnerConnectionHandlerTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static int hostPort;
    private static Path containerIdFile;
    private static Process process;
    private static String containerId;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting OpenGauss for PGPooledConnection.ConnectionHandler tests ...");
        containerIdFile = Files.createTempFile("opengauss-pooled-connection-test-", ".cid");
        Files.delete(containerIdFile);
        process = new ProcessBuilder(
                "docker", "run", "--rm", "--cidfile", containerIdFile.toString(), "-p", "127.0.0.1::5432", "-e",
                "GS_USERNAME=" + USERNAME, "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-pooled-connection-stdout.txt"))
                .redirectError(new File("opengauss-pooled-connection-stderr.txt"))
                .start();
        Awaitility.await().atMost(Duration.ofSeconds(55)).ignoreExceptions().until(() -> {
            discoverMappedPort();
            openConnection().close();
            return true;
        });
        System.out.println("OpenGauss for PGPooledConnection.ConnectionHandler tests started on port " + hostPort);
    }

    @AfterAll
    static void tearDown() throws IOException, InterruptedException {
        if (containerId != null) {
            System.out.println("Shutting down OpenGauss for PGPooledConnection.ConnectionHandler tests");
            runDockerCommand("docker", "stop", containerId);
        }
        if (process != null && process.isAlive()) {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        if (containerIdFile != null) {
            Files.deleteIfExists(containerIdFile);
        }
    }

    @Test
    void delegatesConnectionOperationsAndWrapsCreatedStatements() throws Exception {
        try (Connection physicalConnection = openConnection()) {
            PGPooledConnection pooledConnection = new PGPooledConnection(physicalConnection, false);
            try {
                Connection pooledHandle = pooledConnection.getConnection();

                assertThat(pooledHandle.getAutoCommit()).isFalse();

                try (Statement statement = pooledHandle.createStatement()) {
                    assertThat(statement).isInstanceOf(PGStatement.class);
                    assertThat(statement.getConnection()).isSameAs(pooledHandle);
                }
                try (CallableStatement statement = pooledHandle.prepareCall("SELECT 1")) {
                    assertThat(statement).isInstanceOf(PGStatement.class);
                    assertThat(statement.getConnection()).isSameAs(pooledHandle);
                }
                try (PreparedStatement statement = pooledHandle.prepareStatement("SELECT ?")) {
                    assertThat(statement).isInstanceOf(PGStatement.class);
                    assertThat(statement.getConnection()).isSameAs(pooledHandle);
                }

                pooledHandle.close();
                assertThat(pooledHandle.isClosed()).isTrue();
            } finally {
                pooledConnection.close();
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection("jdbc:postgresql://localhost:" + hostPort + "/" + DATABASE, props);
    }

    private static void discoverMappedPort() throws IOException, InterruptedException {
        if (containerId == null || containerId.isEmpty()) {
            containerId = Files.readString(containerIdFile, StandardCharsets.UTF_8).trim();
        }
        if (containerId.isEmpty()) {
            throw new IOException("OpenGauss container id is not available yet");
        }
        String portMapping = runDockerCommand("docker", "port", containerId, "5432/tcp");
        int lineSeparatorIndex = portMapping.indexOf(System.lineSeparator());
        String firstPortMapping = lineSeparatorIndex == -1 ? portMapping : portMapping.substring(0, lineSeparatorIndex);
        int portSeparatorIndex = firstPortMapping.lastIndexOf(':');
        hostPort = Integer.parseInt(firstPortMapping.substring(portSeparatorIndex + 1));
    }

    private static String runDockerCommand(String... command) throws IOException, InterruptedException {
        Process commandProcess = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!commandProcess.waitFor(5, TimeUnit.SECONDS)) {
            commandProcess.destroyForcibly();
            throw new IOException("Docker command timed out");
        }
        String output = new String(commandProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (commandProcess.exitValue() != 0) {
            throw new IOException(output);
        }
        return output;
    }
}
