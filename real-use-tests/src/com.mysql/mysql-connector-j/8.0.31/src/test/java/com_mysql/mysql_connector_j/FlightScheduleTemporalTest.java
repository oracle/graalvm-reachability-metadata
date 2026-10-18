/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Temporal value mapping and time-zone handling: a flight scheduler stores departures as
/// instants and local wall-clock times through `connectionTimeZone`, `preserveInstants` and
/// `forceConnectionTimeZoneToSession`, round-trips `java.time` values with fractional seconds,
/// and reads `YEAR` columns and zero dates under `yearIsDateType`, `sendFractionalSeconds` and
/// `zeroDateTimeBehavior`.
public class FlightScheduleTemporalTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "flights";
    private static final String USER = "scheduler";
    private static final String PASSWORD = "scheduler-secret";
    private static final String CONTAINER = "real-use-mysql-temporal-" + UUID.randomUUID();

    private static int port;

    @BeforeAll
    static void startMysql() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_RANDOM_ROOT_PASSWORD=yes",
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
        awaitServer(TimeUnit.MINUTES.toMillis(3));
        createSchema();
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void storesDeparturesAsInstantsAcrossSessionTimeZones() throws SQLException {
        ZonedDateTime berlinDeparture = ZonedDateTime.of(2026, 7, 1, 9, 30, 0, 0, ZoneId.of("Europe/Berlin"));
        try (Connection berlin = connect("connectionTimeZone=Europe/Berlin&forceConnectionTimeZoneToSession=true")) {
            assertThat(sessionTimeZone(berlin)).isEqualTo("Europe/Berlin");
            try (PreparedStatement insert = berlin.prepareStatement("""
                    INSERT INTO departure (flight, departs_at, local_departure) VALUES (?, ?, ?)
                    """)) {
                insert.setString(1, "LH400");
                insert.setTimestamp(2, Timestamp.from(berlinDeparture.toInstant()));
                insert.setObject(3, berlinDeparture.toLocalDateTime());
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
        }

        try (Connection utc = connect("connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
             Statement statement = utc.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT departs_at, local_departure,
                            CAST(departs_at AS CHAR) AS departs_text,
                            CAST(local_departure AS CHAR) AS local_text
                     FROM departure WHERE flight = 'LH400'
                     """)) {
            assertThat(sessionTimeZone(utc)).isEqualTo("+00:00");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("departs_text")).isEqualTo("2026-07-01 07:30:00");
            assertThat(rows.getString("local_text")).isEqualTo("2026-07-01 09:30:00");
            assertThat(rows.getTimestamp("departs_at").toInstant())
                    .isEqualTo(Instant.parse("2026-07-01T07:30:00Z"));
            assertThat(rows.getObject("local_departure", LocalDateTime.class))
                    .isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 30));
            assertThat(rows.next()).isFalse();
        }

        try (Connection newYork = connect("connectionTimeZone=America/New_York&forceConnectionTimeZoneToSession=true");
             PreparedStatement query = newYork.prepareStatement("""
                     SELECT departs_at, CAST(departs_at AS CHAR) AS departs_text
                     FROM departure WHERE departs_at < ?
                     """)) {
            query.setTimestamp(1, Timestamp.from(Instant.parse("2026-07-01T08:00:00Z")));
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("departs_text")).isEqualTo("2026-07-01 03:30:00");
                assertThat(rows.getTimestamp("departs_at").toInstant()).isEqualTo(berlinDeparture.toInstant());
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void roundTripsFractionalSecondsAndTimeOfDay() throws SQLException {
        LocalDateTime boarding = LocalDateTime.of(2026, 8, 15, 6, 45, 12, 123_456_000);
        LocalTime gateClose = LocalTime.of(23, 59, 59, 125_000_000);
        LocalDate serviceDay = LocalDate.of(2026, 8, 15);
        try (Connection connection = connect("connectionTimeZone=UTC")) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO boarding (flight, boarding_at, gate_close, service_day, rounded_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, "AF100");
                insert.setObject(2, boarding);
                insert.setObject(3, gateClose);
                insert.setObject(4, serviceDay);
                insert.setObject(5, LocalDateTime.of(2026, 8, 15, 10, 0, 0, 600_000_000));
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT boarding_at, gate_close, service_day, rounded_at FROM boarding WHERE flight = ?")) {
                query.setString(1, "AF100");
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject("boarding_at", LocalDateTime.class)).isEqualTo(boarding);
                    assertThat(rows.getObject("gate_close", LocalTime.class)).isEqualTo(gateClose);
                    assertThat(rows.getObject("service_day", LocalDate.class)).isEqualTo(serviceDay);
                    assertThat(rows.getObject("rounded_at", LocalDateTime.class))
                            .isEqualTo(LocalDateTime.of(2026, 8, 15, 10, 0, 1));
                }
            }
        }

        try (Connection truncating = connect("connectionTimeZone=UTC&sendFractionalSeconds=false")) {
            try (PreparedStatement insert = truncating.prepareStatement("""
                    INSERT INTO boarding (flight, boarding_at, gate_close, service_day, rounded_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, "AF200");
                insert.setObject(2, boarding);
                insert.setObject(3, gateClose);
                insert.setObject(4, serviceDay);
                insert.setObject(5, LocalDateTime.of(2026, 8, 15, 10, 0, 0, 600_000_000));
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (Statement statement = truncating.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT boarding_at, rounded_at FROM boarding WHERE flight = 'AF200'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("boarding_at", LocalDateTime.class))
                        .isEqualTo(LocalDateTime.of(2026, 8, 15, 6, 45, 12));
                assertThat(rows.getObject("rounded_at", LocalDateTime.class))
                        .isEqualTo(LocalDateTime.of(2026, 8, 15, 10, 0, 0));
            }
        }
    }

    @Test
    void mapsYearColumnsAndZeroDatesByConnectionProperties() throws SQLException {
        try (Connection connection = connect("connectionTimeZone=UTC");
             Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION sql_mode = ''");
            statement.executeUpdate("""
                    INSERT INTO fleet (tail_number, built_year, retired_at)
                    VALUES ('D-AIMA', 2009, '0000-00-00 00:00:00')
                    """);
            try (ResultSet rows = statement.executeQuery(
                    "SELECT built_year, retired_at FROM fleet WHERE tail_number = 'D-AIMA'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("built_year")).isEqualTo(Date.valueOf("2009-01-01"));
                assertThatThrownBy(() -> rows.getTimestamp("retired_at"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("Zero date value prohibited");
            }
        }

        try (Connection connection = connect("connectionTimeZone=UTC&yearIsDateType=false"
                + "&zeroDateTimeBehavior=CONVERT_TO_NULL");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT built_year, retired_at FROM fleet WHERE tail_number = 'D-AIMA'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject("built_year")).isEqualTo((short) 2009);
            assertThat(rows.getTimestamp("retired_at")).isNull();
            assertThat(rows.wasNull()).isTrue();
        }

        try (Connection connection = connect("connectionTimeZone=UTC&zeroDateTimeBehavior=ROUND");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT retired_at FROM fleet WHERE tail_number = 'D-AIMA'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject("retired_at", LocalDateTime.class))
                    .isEqualTo(LocalDateTime.of(1, 1, 1, 0, 0));
        }
    }

    private static Connection connect(String properties) throws SQLException {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE + "?" + properties);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        return dataSource.getConnection();
    }

    private static String sessionTimeZone(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT @@session.time_zone")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static void createSchema() throws SQLException {
        try (Connection connection = connect("connectionTimeZone=UTC");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE departure (
                        flight VARCHAR(8) PRIMARY KEY,
                        departs_at TIMESTAMP NOT NULL,
                        local_departure DATETIME NOT NULL
                    ) ENGINE = InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE boarding (
                        flight VARCHAR(8) PRIMARY KEY,
                        boarding_at DATETIME(6) NOT NULL,
                        gate_close TIME(3) NOT NULL,
                        service_day DATE NOT NULL,
                        rounded_at DATETIME NOT NULL
                    ) ENGINE = InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE fleet (
                        tail_number VARCHAR(8) PRIMARY KEY,
                        built_year YEAR NOT NULL,
                        retired_at DATETIME NULL
                    ) ENGINE = InnoDB
                    """);
        }
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = connect("connectionTimeZone=UTC");
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("MySQL did not become reachable", lastFailure);
    }

    private static void docker(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).inheritIO().start();
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("docker %s finished", args[0]).isTrue();
        assertThat(process.exitValue()).as("docker %s exit code", args[0]).isZero();
    }
}
