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

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PgArrayTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:15433/" + DATABASE;
    private static final UUID FIRST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static Process process;

    private static Connection openConnection() throws SQLException {
        return openConnection(new Properties());
    }

    private static Connection openConnection(Properties properties) throws SQLException {
        properties.setProperty("user", USERNAME);
        properties.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, properties);
    }

    private static void assertDeepArrayEquals(Object actual, Object[] expected) {
        assertThat(actual).isInstanceOf(expected.getClass());
        assertThat(Arrays.deepEquals((Object[]) actual, expected)).isTrue();
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting OpenGauss for PgArray tests ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "15433:5432", "-e", "GS_USERNAME=" + USERNAME,
                "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-pg-array-stdout.txt"))
                .redirectError(new File("opengauss-pg-array-stderr.txt")).start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            openConnection().close();
            return true;
        });
        System.out.println("OpenGauss for PgArray tests started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down OpenGauss for PgArray tests");
            process.destroy();
        }
    }

    @Test
    void readsTextArraysAsTypedJavaArrays() throws Exception {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT
                            '{{true,false},{false,true}}'::boolean[] AS bools,
                            '{{1,2},{3,4}}'::int2[] AS shorts,
                            '{{5,6},{7,8}}'::int4[] AS ints,
                            '{{9,10},{11,12}}'::int8[] AS longs,
                            '{{1.25,2.50},{3.75,4.00}}'::numeric[] AS numerics,
                            '{{1.5,2.5},{3.5,4.5}}'::real[] AS floats,
                            '{{5.5,6.5},{7.5,8.5}}'::float8[] AS doubles,
                            '{{alpha,beta},{gamma,delta}}'::text[] AS strings,
                            '{{2020-01-02,2021-03-04},{2022-05-06,2023-07-08}}'::date[] AS dates,
                            '{{01:02:03,04:05:06},{07:08:09,10:11:12}}'::time[] AS times,
                            ('{{"2020-01-02 03:04:05","2021-06-07 08:09:10"},'
                                || '{"2022-11-12 13:14:15","2023-12-13 14:15:16"}}')::timestamp[] AS timestamps,
                            ('{{"00000000-0000-0000-0000-000000000001"},'
                                || '{"00000000-0000-0000-0000-000000000002"}}')::uuid[] AS uuid_matrix,
                            ('{"00000000-0000-0000-0000-000000000001",'
                                || '"00000000-0000-0000-0000-000000000002"}')::uuid[] AS uuid_values
                        """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertDeepArrayEquals(resultSet.getArray("bools").getArray(),
                        new Boolean[][] {{true, false}, {false, true}});
                assertDeepArrayEquals(resultSet.getArray("shorts").getArray(), new Short[][] {{1, 2}, {3, 4}});
                assertDeepArrayEquals(resultSet.getArray("ints").getArray(), new Integer[][] {{5, 6}, {7, 8}});
                assertDeepArrayEquals(resultSet.getArray("longs").getArray(), new Long[][] {{9L, 10L}, {11L, 12L}});
                Object numericArray = resultSet.getArray("numerics").getArray();
                assertThat(numericArray).isInstanceOf(BigDecimal[][].class);
                assertThat(((BigDecimal[][]) numericArray)[0][0]).isEqualByComparingTo("1.25");
                assertThat(((BigDecimal[][]) numericArray)[1][1]).isEqualByComparingTo("4.00");
                assertDeepArrayEquals(resultSet.getArray("floats").getArray(),
                        new Float[][] {{1.5f, 2.5f}, {3.5f, 4.5f}});
                assertDeepArrayEquals(resultSet.getArray("doubles").getArray(),
                        new Double[][] {{5.5d, 6.5d}, {7.5d, 8.5d}});
                assertDeepArrayEquals(resultSet.getArray("strings").getArray(),
                        new String[][] {{"alpha", "beta"}, {"gamma", "delta"}});
                assertDeepArrayEquals(resultSet.getArray("dates").getArray(), new Timestamp[][] {
                        {Timestamp.valueOf("2020-01-02 00:00:00"), Timestamp.valueOf("2021-03-04 00:00:00")},
                        {Timestamp.valueOf("2022-05-06 00:00:00"), Timestamp.valueOf("2023-07-08 00:00:00")}
                });
                assertDeepArrayEquals(resultSet.getArray("times").getArray(), new Time[][] {
                        {Time.valueOf("01:02:03"), Time.valueOf("04:05:06")},
                        {Time.valueOf("07:08:09"), Time.valueOf("10:11:12")}
                });
                assertDeepArrayEquals(resultSet.getArray("timestamps").getArray(), new Timestamp[][] {
                        {Timestamp.valueOf("2020-01-02 03:04:05"), Timestamp.valueOf("2021-06-07 08:09:10")},
                        {Timestamp.valueOf("2022-11-12 13:14:15"), Timestamp.valueOf("2023-12-13 14:15:16")}
                });
                assertDeepArrayEquals(resultSet.getArray("uuid_matrix").getArray(),
                        new UUID[][] {{FIRST_UUID}, {SECOND_UUID}});
                assertDeepArrayEquals(resultSet.getArray("uuid_values").getArray(),
                        new UUID[] {FIRST_UUID, SECOND_UUID});
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    void readsBinaryArraysAsTypedJavaArrays() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("binaryTransfer", "true");
        properties.setProperty("prepareThreshold", "-1");
        try (Connection connection = openConnection(properties);
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT ARRAY[1, 2, 3]::int4[] AS ints, ARRAY[]::int4[] AS empty_ints
                        """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertDeepArrayEquals(resultSet.getArray("ints").getArray(), new Integer[] {1, 2, 3});
                assertDeepArrayEquals(resultSet.getArray("empty_ints").getArray(), new Integer[] {});
                assertThat(resultSet.next()).isFalse();
            }
        }
    }
}
