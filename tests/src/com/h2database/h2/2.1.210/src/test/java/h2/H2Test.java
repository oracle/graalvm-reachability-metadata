/*
 * Licensed under Public Domain (CC0)
 *
 * To the extent possible under law, the person who associated CC0 with
 * this code has waived all copyright and related or neighboring
 * rights to this code.
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.Interval;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Moritz Halbritter
 */
class H2Test {
    @ParameterizedTest
    @ValueSource(strings = {"jdbc:h2:./data/test", "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1"})
    void test(String url) throws Exception {
        // Cleanup
        withConnection(url, connection -> {
            connection.prepareStatement("DROP TABLE IF EXISTS test").execute();
            connection.prepareStatement("DROP TABLE IF EXISTS datatypes").execute();
            connection.commit();
        });

        // Insert data
        withConnection(url, connection -> {
            connection.prepareStatement("CREATE TABLE test(id INTEGER AUTO_INCREMENT, name VARCHAR)").execute();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO test(name) VALUES (?)");
            for (int i = 0; i < 10; i++) {
                statement.setString(1, Integer.toString(i));
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
        });

        // Read data
        withConnection(url, connection -> {
            try (ResultSet resultSet = connection.prepareStatement("SELECT * FROM test").executeQuery()) {
                String[] expectedNames = new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
                for (String name : expectedNames) {
                    resultSet.next();
                    assertThat(resultSet.getString(2)).isEqualTo(name);
                }
                assertThat(resultSet.next()).isFalse();
            }
        });

        // Delete data, then rollback transaction
        withConnection(url, connection -> {
            int deleted = connection.prepareStatement("DELETE FROM test").executeUpdate();
            assertThat(deleted).isEqualTo(10);
            connection.rollback();
        });

        // Check that data is still there
        withConnection(url, connection -> {
            try (ResultSet resultSet = connection.prepareStatement("SELECT COUNT(1) FROM test").executeQuery()) {
                resultSet.next();
                int count = resultSet.getInt(1);
                assertThat(count).isEqualTo(10);
            }
        });

        // Insert data with a lot of data types
        withConnection(url, connection -> {
            connection.prepareStatement("CREATE TABLE datatypes (c1 CHARACTER, c2 CHARACTER VARYING, c3 CHARACTER LARGE OBJECT, v1 VARCHAR_IGNORECASE, b1 BINARY, b2 BINARY VARYING, " +
                    "b3 BINARY LARGE OBJECT, b4 BOOLEAN, t1 TINYINT, s1 SMALLINT, i1 INTEGER, b5 BIGINT, n1 NUMERIC, r1 REAL, d1 DOUBLE PRECISION, d2 DECFLOAT, d3 DATE, t2 TIME, " +
                    "t3 TIME WITH TIME ZONE, t4 TIMESTAMP, t5 TIMESTAMP WITH TIME ZONE, i2 INTERVAL YEAR, j1 JAVA_OBJECT, e1 ENUM('value1', 'value2'), g1 GEOMETRY, j2 JSON, u1 UUID, " +
                    "i3 INTEGER ARRAY, n2 CHARACTER VARYING)").execute();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO datatypes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, "c"); // CHARACTER
            statement.setString(2, "character varying"); // CHARACTER VARYING
            statement.setString(3, "character large object"); // CHARACTER LARGE OBJECT
            statement.setString(4, "varchar ignore case"); // VARCHAR IGNORE CASE
            statement.setBytes(5, new byte[]{42}); // BINARY
            statement.setBytes(6, new byte[]{4, 8, 15, 16, 23, 42}); // BINARY VARYING
            statement.setBytes(7, new byte[]{4, 8, 15, 16, 23, 42, 23, 16, 15, 8, 4}); // BINARY LARGE OBJECT
            statement.setBoolean(8, true); // BOOLEAN
            statement.setInt(9, 4); // TINYINT
            statement.setInt(10, 8); // SMALLINT
            statement.setInt(11, 15); // INTEGER
            statement.setLong(12, Long.MAX_VALUE); // BIGINT
            statement.setBigDecimal(13, new BigDecimal("12345")); // NUMERIC
            statement.setFloat(14, 23.42f); // REAL
            statement.setDouble(15, 42.23); // DOUBLE PRECISION
            statement.setBigDecimal(16, new BigDecimal("12345")); // DECFLOAT
            statement.setObject(17, LocalDate.of(2020, 1, 2)); // DATE
            statement.setObject(18, LocalTime.of(23, 1, 2)); // TIME
            statement.setObject(19, OffsetTime.of(23, 1, 2, 3, ZoneOffset.ofHours(3))); // TIME WITH TIME ZONE
            statement.setObject(20, LocalDateTime.of(2020, 1, 2, 3, 4, 5, 6)); // TIMESTAMP
            statement.setObject(21, OffsetDateTime.of(2020, 1, 2, 3, 4, 5, 6, ZoneOffset.ofHours(3))); // TIMESTAMP WITH TIME ZONE
            statement.setObject(22, Interval.ofYears(23)); // INTERVAL YEAR
            statement.setObject(23, "serialize this string", Types.JAVA_OBJECT); // JAVA_OBJECT
            statement.setString(24, "value2"); // ENUM
            statement.setString(25, "POINT (23 42)"); // GEOMETRY
            statement.setString(26, "{ \"foo\": \"bar\" }"); // JSON
            statement.setObject(27, UUID.fromString("79191aba-a27e-455b-abb6-8eea44c1aacc")); // UUID
            statement.setArray(28, connection.createArrayOf("INTEGER", new Integer[]{4, 8, 15, 16, 23, 42})); // INTEGER ARRAY
            statement.setNull(29, Types.VARCHAR); // NULL
            statement.execute();
            connection.commit();
        });

        // Read data back
        withConnection(url, connection -> {
            try (ResultSet resultSet = connection.prepareStatement("SELECT * FROM datatypes").executeQuery()) {
                resultSet.next();
                for (int i = 1; i <= 29; i++) {
                    System.out.printf("column %d: %s%n", i, resultSet.getObject(i));
                }
            }
        });
    }

    private static void withConnection(String url, ConnectionCallback callback) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            callback.run(connection);
        }
    }

    private interface ConnectionCallback {
        void run(Connection connection) throws SQLException;
    }
}
