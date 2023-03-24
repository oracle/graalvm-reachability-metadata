/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test uses docker to start a postgresql database to test against.
 */
public class PostgresqlTests {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static final String JDBC_URL = "jdbc:postgresql://localhost/" + DATABASE;

    private static Process process;

    private static Connection openConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, props);
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting PostgreSQL ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "5432:5432", "-e", "POSTGRES_DB=" + DATABASE, "-e", "POSTGRES_USER=" + USERNAME,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD, "postgres:15-alpine").redirectOutput(new File("postgres-stdout.txt"))
                .redirectError(new File("postgres-stderr.txt")).start();

        // Wait until connection can be established
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            openConnection().close();
            return true;
        });
        System.out.println("PostgreSQL started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down PostgreSQL");
            process.destroy();
        }
    }

    @Test
    void commitAndRollback() throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            conn.prepareStatement("CREATE TABLE foo (id INT GENERATED ALWAYS AS IDENTITY, name VARCHAR)").execute();
            conn.commit();
        }

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            PreparedStatement statement = conn.prepareStatement("INSERT INTO foo (name) VALUES (?)");
            statement.setString(1, "Adam");
            statement.execute();
            statement.setString(1, "Eve");
            statement.execute();
            conn.commit();
        }

        try (Connection conn = openConnection()) {
            // Test rollbacks
            conn.setAutoCommit(false);
            conn.prepareStatement("DELETE FROM foo").execute();
            conn.rollback();
        }

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try (ResultSet resultSet = conn.prepareStatement("SELECT * FROM foo").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
                assertThat(resultSet.getString(2)).isEqualTo("Adam");
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(2);
                assertThat(resultSet.getString(2)).isEqualTo("Eve");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    void otherDatatypes() throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            connection.prepareStatement("CREATE TABLE other_datatypes(c1 cidr, i1 inet, i2 interval, m1 macaddr, m2 macaddr8, m3 money, n1 numeric(10, 2), t1 tsvector)").execute();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO other_datatypes VALUES (CAST(? as cidr), CAST(? as inet), CAST(? as interval), CAST(? as macaddr), CAST(? as macaddr8), ?, ?, CAST(? as tsvector))");
            statement.setString(1, "192.168.100.128/25"); // cidr
            statement.setString(2, "fe80::20c:29ff:fe8a:cd55/64"); // inet
            statement.setString(3, "1 year 2 months 3 days 4 hours 5 minutes 6 seconds"); // interval
            statement.setString(4, "08:00:2b:01:02:03"); // macaddr
            statement.setString(5, "08:00:2b:01:02:03:04:05"); // macaddr8
            statement.setInt(6, 12); // money
            statement.setDouble(7, Math.PI); // numeric
            statement.setString(8, "this is an example sentence"); // tsvector

            statement.execute();

            try (ResultSet resultSet = connection.prepareStatement("SELECT * FROM other_datatypes").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                for (int i = 1; i <= 8; i++) {
                    System.out.printf("column %d: %s%n", i, resultSet.getObject(i));
                }
            }
        }
    }

    @Test
    void geometricDatatypes() throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            connection.prepareStatement("CREATE TABLE geometric_datatypes(b1 box, c1 circle, l1 line, l2 lseg, p1 path, p2 point, p3 polygon)").execute();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO geometric_datatypes VALUES (CAST(? as box), CAST(? as circle), CAST(? as line), CAST(? as lseg), CAST(? as path), CAST(? as point), CAST(? as polygon))");
            statement.setString(1, "(0, 0), (10, 10)"); // box
            statement.setString(2, "< (0, 0), 5 >"); // circle
            statement.setString(3, "{ 1, 2, 3 }"); // line
            statement.setString(4, "[ ( 0, 1 ), ( 2, 3 ) ]"); // lseg
            statement.setString(5, "[ ( 1, 2 ), ( 3, 4 ), ( 5, 6 ) ]"); // path
            statement.setString(6, "( 1, 2 )"); // point
            statement.setString(7, "( ( 1, 2 ), ( 3, 4 ), ( 5, 6 ) )"); // polygon
            statement.execute();

            try (ResultSet resultSet = connection.prepareStatement("SELECT * FROM geometric_datatypes").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                for (int i = 1; i <= 7; i++) {
                    System.out.printf("column %d: %s%n", i, resultSet.getObject(i));
                }
            }
        }
    }

    @Test
    void simpleDatatypes() throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            connection.prepareStatement("CREATE TABLE simple_datatypes " +
                    "(b1 bigint, b2 bit(1), b3 boolean, b4 bytea, c1 character(1), c2 character varying, d1 date, d2 double precision, " +
                    "i1 integer, j1 json, j2 jsonb, r1 real, s1 smallint, t1 text, t2 time, t3 time with time zone, t4 timestamp, " +
                    "t5 timestamp with time zone, u1 uuid, x1 xml, n1 integer)").execute();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO simple_datatypes VALUES (?, CAST(? AS bit), ?, ?, ?, ?, ?, ?, ?, CAST(? AS json), CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS xml), ?)");
            statement.setLong(1, Long.MAX_VALUE); // bigint
            statement.setString(2, "1"); // bit(1)
            statement.setBoolean(3, true); // boolean
            statement.setObject(4, "some-bytes".getBytes(StandardCharsets.UTF_8)); // bytea
            statement.setString(5, "c"); // character(1)
            statement.setString(6, "some-string"); // character varying
            statement.setDate(7, Date.valueOf(LocalDate.of(2020, 1, 2))); // date
            statement.setDouble(8, Math.PI); // double precision
            statement.setInt(9, 42); // integer
            statement.setString(10, "{ \"foo\": \"bar\"}"); // json
            statement.setString(11, "{ \"foo\": \"bar\"}"); // jsonb
            statement.setFloat(12, (float) Math.PI); // real
            // s1 smallint, t1 text, t2 time, t3 time with time zone, t4 timestamp, t5 timestamp with time zone, u1 uuid, x1 xml)").execute();
            statement.setInt(13, 5); // smallint
            statement.setString(14, "some-text"); // text
            statement.setObject(15, LocalTime.of(23, 59)); // time
            statement.setObject(16, OffsetTime.of(23, 59, 0, 0, ZoneOffset.ofHours(2))); // time with time zone
            statement.setObject(17, LocalDateTime.of(2020, 1, 2, 23, 59, 0)); // timestamp
            statement.setObject(18, OffsetDateTime.of(2020, 1, 2, 23, 59, 0, 0, ZoneOffset.ofHours(3))); // timestamp with time zone
            statement.setObject(19, UUID.randomUUID()); // uuid
            statement.setString(20, "<foo><bar></bar></foo>");
            statement.setNull(21, Types.INTEGER);
            statement.execute();

            try (ResultSet resultSet = connection.prepareStatement("SELECT * FROM simple_datatypes").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                for (int i = 1; i <= 21; i++) {
                    System.out.printf("column %d: %s%n", i, resultSet.getObject(i));
                }
            }
        }
    }
}
