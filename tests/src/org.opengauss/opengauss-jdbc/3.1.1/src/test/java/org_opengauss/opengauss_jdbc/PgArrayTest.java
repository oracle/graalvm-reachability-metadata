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
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class PgArrayTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:15433/" + DATABASE;

    private static Process process;
    private static Connection connection;
    private static BaseConnection baseConnection;

    private static Connection openConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, props);
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        System.out.println("Starting OpenGauss for PgArray coverage ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "15433:5432", "-e", "GS_USERNAME=" + USERNAME,
                "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-pgarray-stdout.txt"))
                .redirectError(new File("opengauss-pgarray-stderr.txt"))
                .start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            openConnection().close();
            return true;
        });
        connection = openConnection();
        baseConnection = connection.unwrap(BaseConnection.class);
        System.out.println("OpenGauss started for PgArray coverage");
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down OpenGauss for PgArray coverage");
            process.destroy();
        }
    }

    @Test
    void textArraysCreateTypedMultidimensionalJavaArrays() throws Exception {
        assertArrayEquals(
                new Boolean[][]{{true, false}, {null, true}},
                (Object[]) textArray(Oid.BOOL_ARRAY, "{{true,false},{NULL,true}}"));
        assertArrayEquals(
                new Short[][]{{1, 2}, {3, 4}},
                (Object[]) textArray(Oid.INT2_ARRAY, "{{1,2},{3,4}}"));
        assertArrayEquals(
                new Integer[][]{{10, 20}, {30, 40}},
                (Object[]) textArray(Oid.INT4_ARRAY, "{{10,20},{30,40}}"));
        assertArrayEquals(
                new Long[][]{{100L, 200L}, {300L, 400L}},
                (Object[]) textArray(Oid.INT8_ARRAY, "{{100,200},{300,400}}"));
        assertArrayEquals(
                new BigDecimal[][]{
                        {new BigDecimal("1.25"), new BigDecimal("2.50")},
                        {new BigDecimal("3.75"), new BigDecimal("4.00")}
                },
                (Object[]) textArray(Oid.NUMERIC_ARRAY, "{{1.25,2.50},{3.75,4.00}}"));
        assertArrayEquals(
                new Float[][]{{1.5f, 2.5f}, {3.5f, 4.5f}},
                (Object[]) textArray(Oid.FLOAT4_ARRAY, "{{1.5,2.5},{3.5,4.5}}"));
        assertArrayEquals(
                new Double[][]{{1.5d, 2.5d}, {3.5d, 4.5d}},
                (Object[]) textArray(Oid.FLOAT8_ARRAY, "{{1.5,2.5},{3.5,4.5}}"));
        assertArrayEquals(
                new String[][]{{"alpha", "beta"}, {"gamma", "delta"}},
                (Object[]) textArray(Oid.VARCHAR_ARRAY, "{{alpha,beta},{gamma,delta}}"));
        assertArrayEquals(
                new Date[][]{
                        {Date.valueOf("2020-01-02"), Date.valueOf("2020-01-03")},
                        {Date.valueOf("2020-01-04"), Date.valueOf("2020-01-05")}
                },
                (Object[]) textArray(Oid.DATE_ARRAY, "{{2020-01-02,2020-01-03},{2020-01-04,2020-01-05}}"));
        assertArrayEquals(
                new Time[][]{
                        {Time.valueOf("12:34:56"), Time.valueOf("13:34:56")},
                        {Time.valueOf("14:34:56"), Time.valueOf("15:34:56")}
                },
                (Object[]) textArray(Oid.TIME_ARRAY, "{{12:34:56,13:34:56},{14:34:56,15:34:56}}"));
        assertArrayEquals(
                new Timestamp[][]{
                        {
                                Timestamp.valueOf("2020-01-02 03:04:05"),
                                Timestamp.valueOf("2020-01-03 03:04:05")
                        },
                        {
                                Timestamp.valueOf("2020-01-04 03:04:05"),
                                Timestamp.valueOf("2020-01-05 03:04:05")
                        }
                },
                (Object[]) textArray(
                        Oid.TIMESTAMP_ARRAY,
                        "{{\"2020-01-02 03:04:05\",\"2020-01-03 03:04:05\"},"
                                + "{\"2020-01-04 03:04:05\",\"2020-01-05 03:04:05\"}}"));
    }

    @Test
    void uuidArraysUseArrayAssistantForOneAndTwoDimensionalArrays() throws Exception {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID fourth = UUID.fromString("00000000-0000-0000-0000-000000000004");

        assertArrayEquals(
                new UUID[]{first, second},
                (Object[]) textArray(Oid.UUID_ARRAY, "{" + first + "," + second + "}"));
        assertArrayEquals(
                new UUID[][]{{first, second}, {third, fourth}},
                (Object[]) textArray(
                        Oid.UUID_ARRAY,
                        "{{" + first + "," + second + "},{" + third + "," + fourth + "}}"));
    }

    @Test
    void binaryArraysCreateTypedJavaArrays() throws Exception {
        assertArrayEquals(
                new Integer[0],
                (Object[]) new PgArray(baseConnection, Oid.INT4_ARRAY, emptyBinaryArray(Oid.INT4)).getArray());
        assertArrayEquals(
                new Integer[]{11, 22, 33},
                (Object[]) new PgArray(baseConnection, Oid.INT4_ARRAY, binaryInt4Array(11, 22, 33)).getArray());
    }

    private static Object textArray(int oid, String literal) throws SQLException {
        return new PgArray(baseConnection, oid, literal).getArray();
    }

    private static byte[] emptyBinaryArray(int elementOid) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(elementOid);
        return bytes.toByteArray();
    }

    private static byte[] binaryInt4Array(int... values) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(1);
        output.writeInt(0);
        output.writeInt(Oid.INT4);
        output.writeInt(values.length);
        output.writeInt(1);
        for (int value : values) {
            output.writeInt(Integer.BYTES);
            output.writeInt(value);
        }
        return bytes.toByteArray();
    }
}
