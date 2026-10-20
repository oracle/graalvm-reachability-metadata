/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.PGStatement;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.util.PGInterval;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Books hotel stays with PostgreSQL date, time, timestamp, timestamptz and interval columns
 * bound and read through the driver's temporal type mapping (`java.time` values, `PGInterval`
 * and the `PGStatement` infinity sentinels).
 *
 * A reservation desk stores check-in dates, arrival times and confirmation instants as
 * `java.time` values and reads them back unchanged whatever the session time zone, books
 * stay lengths as `PGInterval` values that the server and the client add to dates alike, and
 * keeps open-ended loyalty memberships as `infinity` dates mapped to `LocalDate.MAX` and
 * `PGStatement.DATE_POSITIVE_INFINITY`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HotelStayTemporalTypesTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "hotel";

    private static final String USER = "frontdesk";

    private static final String PASSWORD = "s3cret";

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private PGSimpleDataSource dataSource;

    @BeforeAll
    void startDatabase() throws Exception {
        int port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":5432",
                "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                POSTGRES_IMAGE)
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();

        dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {"127.0.0.1"});
        dataSource.setPortNumbers(new int[] {port});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        dataSource.setConnectTimeout(5);
        awaitDatabase();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE reservations (
                        id           serial      PRIMARY KEY,
                        guest        text        NOT NULL,
                        check_in     date        NOT NULL,
                        arrival      time        NOT NULL,
                        requested_at timestamp   NOT NULL,
                        confirmed_at timestamptz NOT NULL,
                        stay         interval    NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE memberships (
                        guest        text      PRIMARY KEY,
                        valid_until  date      NOT NULL,
                        expires_at   timestamp NOT NULL
                    )""");
        }
    }

    @BeforeEach
    void clearBookings() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE reservations, memberships");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void javaTimeValuesRoundTripWhateverTheSessionTimeZone() throws Exception {
        LocalDate checkIn = LocalDate.of(2024, 2, 29);
        LocalTime arrival = LocalTime.of(15, 45, 30, 250_000_000);
        LocalDateTime requestedAt = LocalDateTime.of(2024, 1, 10, 9, 5, 7, 123_456_000);
        OffsetDateTime confirmedAt = OffsetDateTime.of(2024, 1, 10, 11, 30, 0, 0, ZoneOffset.ofHours(2));

        try (Connection connection = dataSource.getConnection()) {
            int id = book(connection, "Ada", checkIn, arrival, requestedAt, confirmedAt,
                    new PGInterval(0, 0, 3, 0, 0, 0));

            setTimeZone(connection, "America/New_York");
            assertReservation(connection, id, checkIn, arrival, requestedAt, confirmedAt);
            assertThat(serverText(connection, id, "confirmed_at")).isEqualTo("2024-01-10 04:30:00-05");

            setTimeZone(connection, "Asia/Tokyo");
            assertReservation(connection, id, checkIn, arrival, requestedAt, confirmedAt);
            assertThat(serverText(connection, id, "confirmed_at")).isEqualTo("2024-01-10 18:30:00+09");

            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT check_in + 1 AS next_day,
                           confirmed_at AT TIME ZONE 'UTC' AS confirmed_utc,
                           requested_at + time '01:00' AS reminder
                    FROM reservations WHERE id = ?""")) {
                query.setInt(1, id);
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getObject("next_day", LocalDate.class)).isEqualTo(LocalDate.of(2024, 3, 1));
                    assertThat(row.getObject("confirmed_utc", LocalDateTime.class))
                            .isEqualTo(LocalDateTime.of(2024, 1, 10, 9, 30));
                    assertThat(row.getObject("reminder", LocalDateTime.class)).isEqualTo(requestedAt.plusHours(1));
                }
            }
        }
    }

    @Test
    void stayLengthsAreIntervalsAddedByServerAndClientAlike() throws Exception {
        PGInterval weekend = new PGInterval(0, 0, 2, 20, 30, 15.5);
        PGInterval longStay = new PGInterval("1 mon 10 days");
        LocalDate checkIn = LocalDate.of(2024, 3, 1);

        try (Connection connection = dataSource.getConnection()) {
            int weekendId = book(connection, "Grace", checkIn, LocalTime.NOON, checkIn.atStartOfDay().minusDays(7),
                    OffsetDateTime.of(2024, 2, 23, 8, 0, 0, 0, ZoneOffset.UTC), weekend);
            int longId = book(connection, "Linus", checkIn, LocalTime.NOON, checkIn.atStartOfDay().minusDays(30),
                    OffsetDateTime.of(2024, 1, 31, 8, 0, 0, 0, ZoneOffset.UTC), longStay);

            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT stay, check_in + stay AS check_out, justify_hours(stay * 2) AS doubled
                    FROM reservations WHERE id = ?""")) {
                query.setInt(1, weekendId);
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    PGInterval stored = (PGInterval) row.getObject("stay");
                    assertThat(stored).isEqualTo(weekend);
                    assertThat(stored.getDays()).isEqualTo(2);
                    assertThat(stored.getHours()).isEqualTo(20);
                    assertThat(stored.getMinutes()).isEqualTo(30);
                    assertThat(stored.getWholeSeconds()).isEqualTo(15);
                    assertThat(stored.getMicroSeconds()).isEqualTo(500_000);
                    assertThat(row.getObject("check_out", LocalDateTime.class))
                            .isEqualTo(LocalDateTime.of(2024, 3, 3, 20, 30, 15, 500_000_000));

                    PGInterval doubled = (PGInterval) row.getObject("doubled");
                    assertThat(doubled.getDays()).isEqualTo(5);
                    assertThat(doubled.getHours()).isEqualTo(17);
                    assertThat(doubled.getMinutes()).isEqualTo(0);
                    assertThat(doubled.getWholeSeconds()).isEqualTo(31);

                    PGInterval clientDoubled = new PGInterval(weekend.getValue());
                    clientDoubled.scale(2);
                    assertThat(clientDoubled.getDays()).isEqualTo(4);
                    assertThat(clientDoubled.getHours()).isEqualTo(40);
                }

                query.setInt(1, longId);
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    PGInterval stored = (PGInterval) row.getObject("stay");
                    assertThat(stored.getMonths()).isEqualTo(1);
                    assertThat(stored.getDays()).isEqualTo(10);
                    assertThat(row.getObject("check_out", LocalDateTime.class))
                            .isEqualTo(LocalDateTime.of(2024, 4, 11, 0, 0));

                    Calendar clientCheckOut = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                    clientCheckOut.clear();
                    clientCheckOut.set(2024, Calendar.MARCH, 1);
                    stored.add(clientCheckOut);
                    assertThat(clientCheckOut.get(Calendar.YEAR)).isEqualTo(2024);
                    assertThat(clientCheckOut.get(Calendar.MONTH)).isEqualTo(Calendar.APRIL);
                    assertThat(clientCheckOut.get(Calendar.DAY_OF_MONTH)).isEqualTo(11);
                }
            }

            PGInterval extension = new PGInterval(0, 0, 1, 2, 0, 0);
            try (PreparedStatement extend = connection.prepareStatement(
                    "UPDATE reservations SET stay = stay + ? WHERE id = ? RETURNING stay")) {
                extend.setObject(1, extension);
                extend.setInt(2, weekendId);
                try (ResultSet row = extend.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    PGInterval expected = new PGInterval(weekend.getValue());
                    extension.add(expected);
                    assertThat(expected).isEqualTo(new PGInterval(0, 0, 3, 22, 30, 15.5));
                    assertThat(row.getObject(1)).isEqualTo(expected);
                }
            }
        }
    }

    @Test
    void openEndedMembershipsUseInfinityDates() throws Exception {
        LocalDate seasonEnd = LocalDate.of(2024, 12, 31);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO memberships (guest, valid_until, expires_at) VALUES (?, ?, ?)")) {
                insert.setString(1, "Ada");
                insert.setObject(2, LocalDate.MAX);
                insert.setTimestamp(3, new Timestamp(PGStatement.DATE_POSITIVE_INFINITY));
                insert.executeUpdate();

                insert.setString(1, "Grace");
                insert.setObject(2, seasonEnd);
                insert.setObject(3, seasonEnd.atTime(23, 59, 59));
                insert.executeUpdate();
            }

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("""
                            SELECT guest, valid_until, expires_at, isfinite(valid_until) AS finite,
                                   valid_until::text AS raw
                            FROM memberships ORDER BY valid_until DESC""")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("guest")).isEqualTo("Ada");
                assertThat(rows.getBoolean("finite")).isFalse();
                assertThat(rows.getString("raw")).isEqualTo("infinity");
                assertThat(rows.getObject("valid_until", LocalDate.class)).isEqualTo(LocalDate.MAX);
                assertThat(rows.getTimestamp("expires_at").getTime()).isEqualTo(PGStatement.DATE_POSITIVE_INFINITY);

                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("guest")).isEqualTo("Grace");
                assertThat(rows.getBoolean("finite")).isTrue();
                assertThat(rows.getObject("valid_until", LocalDate.class)).isEqualTo(seasonEnd);
                assertThat(rows.getObject("expires_at", LocalDateTime.class))
                        .isEqualTo(seasonEnd.atTime(23, 59, 59));
                assertThat(rows.next()).isFalse();
            }

            try (PreparedStatement active = connection.prepareStatement(
                    "SELECT guest FROM memberships WHERE valid_until > ? ORDER BY guest")) {
                active.setObject(1, LocalDate.of(2025, 6, 1));
                try (ResultSet rows = active.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("Ada");
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    private static int book(Connection connection, String guest, LocalDate checkIn, LocalTime arrival,
            LocalDateTime requestedAt, OffsetDateTime confirmedAt, PGInterval stay) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO reservations (guest, check_in, arrival, requested_at, confirmed_at, stay)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id""")) {
            insert.setString(1, guest);
            insert.setObject(2, checkIn);
            insert.setObject(3, arrival);
            insert.setObject(4, requestedAt);
            insert.setObject(5, confirmedAt);
            insert.setObject(6, stay);
            try (ResultSet generated = insert.executeQuery()) {
                assertThat(generated.next()).isTrue();
                return generated.getInt(1);
            }
        }
    }

    private static void assertReservation(Connection connection, int id, LocalDate checkIn, LocalTime arrival,
            LocalDateTime requestedAt, OffsetDateTime confirmedAt) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT check_in, arrival, requested_at, confirmed_at FROM reservations WHERE id = ?")) {
            query.setInt(1, id);
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("check_in", LocalDate.class)).isEqualTo(checkIn);
                assertThat(row.getObject("arrival", LocalTime.class)).isEqualTo(arrival);
                assertThat(row.getObject("requested_at", LocalDateTime.class)).isEqualTo(requestedAt);
                OffsetDateTime confirmed = row.getObject("confirmed_at", OffsetDateTime.class);
                assertThat(confirmed.getOffset()).isEqualTo(ZoneOffset.UTC);
                assertThat(confirmed.isEqual(confirmedAt)).isTrue();
            }
        }
    }

    private static String serverText(Connection connection, int id, String column) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT " + column + "::text FROM reservations WHERE id = ?")) {
            query.setInt(1, id);
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getString(1);
            }
        }
    }

    private static void setTimeZone(Connection connection, String zone) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TIME ZONE '" + zone + "'");
        }
    }

    private void awaitDatabase() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = dataSource.getConnection(); Statement statement = probe.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("PostgreSQL did not accept connections in " + containerName, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
