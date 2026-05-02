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
import org.postgresql.core.BaseConnection;
import org.postgresql.core.Oid;
import org.postgresql.jdbc.PgArray;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PgArrayTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static int hostPort;
    private static Path containerIdFile;
    private static Process process;
    private static String containerId;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting OpenGauss for PgArray tests ...");
        containerIdFile = Files.createTempFile("opengauss-pg-array-test-", ".cid");
        Files.delete(containerIdFile);
        process = new ProcessBuilder(
                "docker", "run", "--rm", "--cidfile", containerIdFile.toString(), "-p", "127.0.0.1::5432", "-e",
                "GS_USERNAME=" + USERNAME, "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-pg-array-stdout.txt"))
                .redirectError(new File("opengauss-pg-array-stderr.txt"))
                .start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            discoverMappedPort();
            openConnection().close();
            return true;
        });
        System.out.println("OpenGauss for PgArray tests started on port " + hostPort);
    }

    @AfterAll
    static void tearDown() throws IOException, InterruptedException {
        if (containerId != null) {
            System.out.println("Shutting down OpenGauss for PgArray tests");
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
    void buildsTwoDimensionalPrimitiveWrapperArraysFromTextValues() throws Exception {
        try (Connection connection = openConnection()) {
            BaseConnection pgConnection = asPgConnection(connection);

            assertThat(arrayFromText(pgConnection, Oid.BOOL_ARRAY, "{{1,0},{t,f}}"))
                    .isEqualTo(new Boolean[][]{{true, false}, {true, false}});
            assertThat(arrayFromText(pgConnection, Oid.INT2_ARRAY, "{{1,2},{3,4}}"))
                    .isEqualTo(new Short[][]{{(short) 1, (short) 2}, {(short) 3, (short) 4}});
            assertThat(arrayFromText(pgConnection, Oid.INT4_ARRAY, "{{1,2},{3,4}}"))
                    .isEqualTo(new Integer[][]{{1, 2}, {3, 4}});
            assertThat(arrayFromText(pgConnection, Oid.INT8_ARRAY, "{{1,2},{3,4}}"))
                    .isEqualTo(new Long[][]{{1L, 2L}, {3L, 4L}});
            assertThat(arrayFromText(pgConnection, Oid.FLOAT4_ARRAY, "{{1.25,2.5},{3.75,4.5}}"))
                    .isEqualTo(new Float[][]{{1.25f, 2.5f}, {3.75f, 4.5f}});
            assertThat(arrayFromText(pgConnection, Oid.FLOAT8_ARRAY, "{{1.25,2.5},{3.75,4.5}}"))
                    .isEqualTo(new Double[][]{{1.25d, 2.5d}, {3.75d, 4.5d}});
        }
    }

    @Test
    void buildsTwoDimensionalObjectArraysFromTextValues() throws Exception {
        try (Connection connection = openConnection()) {
            BaseConnection pgConnection = asPgConnection(connection);

            assertThat(arrayFromText(pgConnection, Oid.NUMERIC_ARRAY, "{{1.25,2.50},{3.75,4.50}}"))
                    .isEqualTo(new BigDecimal[][]{
                            {new BigDecimal("1.25"), new BigDecimal("2.50")},
                            {new BigDecimal("3.75"), new BigDecimal("4.50")}
                    });
            assertThat(arrayFromText(pgConnection, Oid.TEXT_ARRAY, "{{alpha,beta},{gamma,delta}}"))
                    .isEqualTo(new String[][]{{"alpha", "beta"}, {"gamma", "delta"}});
            assertThat(arrayFromText(pgConnection, Oid.DATE_ARRAY, "{{2020-01-01,2020-01-02},{2020-01-03,2020-01-04}}"))
                    .isInstanceOf(Date[][].class);
            assertThat(arrayFromText(pgConnection, Oid.TIME_ARRAY, "{{01:02:03,04:05:06},{07:08:09,10:11:12}}"))
                    .isInstanceOf(Time[][].class);
            assertThat(arrayFromText(pgConnection, Oid.TIMESTAMP_ARRAY,
                    "{{\"2020-01-01 01:02:03\",\"2020-01-02 04:05:06\"},"
                            + "{\"2020-01-03 07:08:09\",\"2020-01-04 10:11:12\"}}"))
                    .isInstanceOf(Timestamp[][].class);
        }
    }

    @Test
    void buildsUuidArraysThroughArrayAssistant() throws Exception {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID fourth = UUID.fromString("00000000-0000-0000-0000-000000000004");

        try (Connection connection = openConnection()) {
            BaseConnection pgConnection = asPgConnection(connection);

            assertThat(arrayFromText(pgConnection, Oid.UUID_ARRAY, "{" + first + "," + second + "}"))
                    .isEqualTo(new UUID[]{first, second});
            assertThat(arrayFromText(pgConnection, Oid.UUID_ARRAY,
                    "{{" + first + "," + second + "},{" + third + "," + fourth + "}}"))
                    .isEqualTo(new UUID[][]{{first, second}, {third, fourth}});
        }
    }

    @Test
    void buildsBinaryArraysFromWireFormat() throws Exception {
        try (Connection connection = openConnection()) {
            BaseConnection pgConnection = asPgConnection(connection);

            assertThat(new PgArray(pgConnection, Oid.INT4_ARRAY, binaryArrayHeaderOnly(Oid.INT4)).getArray())
                    .isEqualTo(new Integer[0]);
            assertThat(new PgArray(pgConnection, Oid.INT4_ARRAY, binaryIntArray(2, 2, 1, 2, 3, 4)).getArray())
                    .isEqualTo(new Integer[][]{{1, 2}, {3, 4}});
        }
    }

    private static Object arrayFromText(BaseConnection connection, int oid, String value) throws SQLException {
        return new PgArray(connection, oid, value).getArray();
    }

    private static BaseConnection asPgConnection(Connection connection) {
        return (BaseConnection) connection;
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

    private static byte[] binaryArrayHeaderOnly(int elementOid) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(elementOid);
        return bytes.toByteArray();
    }

    private static byte[] binaryIntArray(int rows, int columns, int... values) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(2);
        output.writeInt(0);
        output.writeInt(Oid.INT4);
        output.writeInt(rows);
        output.writeInt(1);
        output.writeInt(columns);
        output.writeInt(1);
        for (int value : values) {
            output.writeInt(Integer.BYTES);
            output.writeInt(value);
        }
        return bytes.toByteArray();
    }
}
