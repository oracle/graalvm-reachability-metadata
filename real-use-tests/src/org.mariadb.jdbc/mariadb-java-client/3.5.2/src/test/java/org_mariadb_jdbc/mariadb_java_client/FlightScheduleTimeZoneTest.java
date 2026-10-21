/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb_jdbc.mariadb_java_client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Temporal value mapping and connection time zones: `java.time` values bound with
/// `setObject` and read with `getObject(int, Class)` across `DATE`, `TIME`, `DATETIME`
/// and `TIMESTAMP` columns over the text and binary protocols, with
/// `connectionTimeZone`, `forceConnectionTimeZoneToSession` and `preserveInstants`.
///
/// The scenario is an airline schedule service whose booking offices sit in different
/// time zones and share one MariaDB server: departures are calendar values that must
/// never shift, while ticket purchases are instants every office must agree on.
public class FlightScheduleTimeZoneTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-timezones-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "airline";

    private static final String USER = "scheduler";

    private static final String PASSWORD = "scheduler-secret";

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static final String ZONE_OPTIONS = "&forceConnectionTimeZoneToSession=true&preserveInstants=true";

    private static String url;

    private static MariaDbDataSource textProtocolSource;

    private static MariaDbDataSource binaryProtocolSource;

    private static MariaDbDataSource tokyoOffice;

    private static MariaDbDataSource newYorkOffice;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_DATABASE=" + DATABASE,
                "-e", "MARIADB_USER=" + USER,
                "-e", "MARIADB_PASSWORD=" + PASSWORD,
                "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true",
                "-e", "TZ=UTC",
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        url = "jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE;

        textProtocolSource = dataSource("useServerPrepStmts=false");
        binaryProtocolSource = dataSource("useServerPrepStmts=true");
        tokyoOffice = dataSource("connectionTimeZone=" + TOKYO.getId() + ZONE_OPTIONS);
        newYorkOffice = dataSource("connectionTimeZone=" + NEW_YORK.getId() + ZONE_OPTIONS);
        awaitServer(textProtocolSource);

        try (Connection connection = textProtocolSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE departures (
                        flight VARCHAR(8) NOT NULL PRIMARY KEY,
                        service_date DATE NOT NULL,
                        gate_opens TIME(3) NOT NULL,
                        scheduled_off_block DATETIME(6) NOT NULL,
                        block_time TIME NOT NULL
                    ) ENGINE=InnoDB""");
            statement.execute("""
                    CREATE TABLE tickets (
                        ticket VARCHAR(12) NOT NULL PRIMARY KEY,
                        purchased_at TIMESTAMP(3) NOT NULL,
                        office_wall_clock DATETIME(3) NOT NULL
                    ) ENGINE=InnoDB""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @BeforeEach
    void clearSchedule() throws SQLException {
        try (Connection connection = textProtocolSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM departures");
            statement.execute("DELETE FROM tickets");
        }
    }

    @Test
    void departuresKeepTheirCalendarValuesOverBothProtocols() throws SQLException {
        Departure redEye = new Departure("NH10", LocalDate.of(2026, 3, 29), LocalTime.of(23, 5, 0, 250_000_000),
                LocalDateTime.of(2026, 3, 29, 23, 55, 12, 123_456_000), Duration.ofHours(12).plusMinutes(40));
        Departure leapDay = new Departure("JL6", LocalDate.of(2028, 2, 29), LocalTime.of(6, 0),
                LocalDateTime.of(2028, 2, 29, 6, 45), Duration.ofMinutes(95));

        new Timetable(textProtocolSource).publish(redEye);
        new Timetable(binaryProtocolSource).publish(leapDay);

        for (MariaDbDataSource source : List.of(textProtocolSource, binaryProtocolSource)) {
            Timetable timetable = new Timetable(source);
            assertThat(timetable.departure("NH10")).isEqualTo(redEye);
            assertThat(timetable.departure("JL6")).isEqualTo(leapDay);
            assertThat(timetable.departuresOn(LocalDate.of(2026, 3, 29))).containsExactly("NH10");
        }
    }

    @Test
    void officesForceTheirZoneOntoTheServerSession() throws SQLException {
        assertThat(sessionOffset(tokyoOffice)).isEqualTo("09:00:00");
        assertThat(sessionOffset(newYorkOffice)).isIn("-05:00:00", "-04:00:00");
    }

    @Test
    void everyOfficeSeesTheSamePurchaseInstant() throws SQLException {
        OffsetDateTime purchasedInParis = OffsetDateTime.of(2026, 7, 14, 9, 30, 15, 500_000_000, ZoneOffset.ofHours(2));
        Instant purchase = purchasedInParis.toInstant();

        new TicketDesk(tokyoOffice).sell("TKT-0001", purchasedInParis);

        TicketDesk tokyo = new TicketDesk(tokyoOffice);
        TicketDesk newYork = new TicketDesk(newYorkOffice);
        assertThat(tokyo.purchaseInstant("TKT-0001")).isEqualTo(purchase);
        assertThat(newYork.purchaseInstant("TKT-0001")).isEqualTo(purchase);
        assertThat(newYork.purchaseAsOffset("TKT-0001").toInstant()).isEqualTo(purchase);
        assertThat(newYork.purchaseAsZoned("TKT-0001").toInstant()).isEqualTo(purchase);
        assertThat(tokyo.purchaseSessionClockText("TKT-0001")).isEqualTo("2026-07-14 16:30:15.500");
        assertThat(newYork.purchaseSessionClockText("TKT-0001")).isEqualTo("2026-07-14 03:30:15.500");
        assertThat(tokyo.purchaseEpochSecond("TKT-0001")).isEqualTo(purchase.getEpochSecond());
    }

    @Test
    void zonedPurchasesLandAsTheSellingOfficesWallClockInDatetimeColumns() throws SQLException {
        ZonedDateTime purchasedInLondon = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneId.of("Europe/London"));

        new TicketDesk(newYorkOffice).sell("TKT-0002", purchasedInLondon.toOffsetDateTime());

        TicketDesk tokyo = new TicketDesk(tokyoOffice);
        assertThat(tokyo.purchaseInstant("TKT-0002")).isEqualTo(purchasedInLondon.toInstant());
        assertThat(tokyo.officeWallClockText("TKT-0002")).isEqualTo("2026-12-01 13:00:00.000");
    }

    private static String sessionOffset(MariaDbDataSource office) throws SQLException {
        try (Connection connection = office.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(
                        "SELECT CAST(TIMEDIFF(NOW(), UTC_TIMESTAMP()) AS CHAR)")) {
            assertThat(row.next()).isTrue();
            return row.getString(1);
        }
    }

    private static MariaDbDataSource dataSource(String options) throws SQLException {
        MariaDbDataSource source = new MariaDbDataSource(url + "?" + options);
        source.setUser(USER);
        source.setPassword(PASSWORD);
        return source;
    }

    private static void awaitServer(MariaDbDataSource source) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = source.getConnection()) {
                if (connection.isValid(5)) {
                    return;
                }
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("MariaDB did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    /// A scheduled departure in the airport's local calendar, which no zone may shift.
    record Departure(String flight, LocalDate serviceDate, LocalTime gateOpens,
            LocalDateTime scheduledOffBlock, Duration blockTime) {
    }

    /// Publishes and looks up departures as calendar values.
    static final class Timetable {

        private final MariaDbDataSource source;

        Timetable(MariaDbDataSource source) {
            this.source = source;
        }

        void publish(Departure departure) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO departures (flight, service_date, gate_opens, scheduled_off_block, block_time)
                            VALUES (?, ?, ?, ?, ?)""")) {
                insert.setString(1, departure.flight());
                insert.setObject(2, departure.serviceDate());
                insert.setObject(3, departure.gateOpens());
                insert.setObject(4, departure.scheduledOffBlock());
                insert.setObject(5, departure.blockTime());
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
        }

        Departure departure(String flight) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement query = connection.prepareStatement("""
                            SELECT service_date, gate_opens, scheduled_off_block, block_time
                            FROM departures WHERE flight = ?""")) {
                query.setString(1, flight);
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    return new Departure(flight,
                            row.getObject(1, LocalDate.class),
                            row.getObject(2, LocalTime.class),
                            row.getObject(3, LocalDateTime.class),
                            row.getObject(4, Duration.class));
                }
            }
        }

        List<String> departuresOn(LocalDate serviceDate) throws SQLException {
            List<String> flights = new ArrayList<>();
            try (Connection connection = source.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT flight FROM departures WHERE service_date = ? ORDER BY flight")) {
                query.setObject(1, serviceDate);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        flights.add(rows.getString(1));
                    }
                }
            }
            return flights;
        }
    }

    /// Sells tickets and reports purchase times as each booking office sees them.
    static final class TicketDesk {

        private final MariaDbDataSource office;

        TicketDesk(MariaDbDataSource office) {
            this.office = office;
        }

        void sell(String ticket, OffsetDateTime purchasedAt) throws SQLException {
            try (Connection connection = office.getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO tickets (ticket, purchased_at, office_wall_clock) VALUES (?, ?, ?)")) {
                insert.setString(1, ticket);
                insert.setObject(2, purchasedAt);
                insert.setObject(3, purchasedAt);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
        }

        Instant purchaseInstant(String ticket) throws SQLException {
            return purchase(ticket, "purchased_at", Instant.class);
        }

        OffsetDateTime purchaseAsOffset(String ticket) throws SQLException {
            return purchase(ticket, "purchased_at", OffsetDateTime.class);
        }

        ZonedDateTime purchaseAsZoned(String ticket) throws SQLException {
            return purchase(ticket, "purchased_at", ZonedDateTime.class);
        }

        /// Renders the purchase on the server, in the zone the office forced onto its session.
        String purchaseSessionClockText(String ticket) throws SQLException {
            return purchase(ticket, "CAST(purchased_at AS CHAR)", String.class);
        }

        long purchaseEpochSecond(String ticket) throws SQLException {
            return purchase(ticket, "FLOOR(UNIX_TIMESTAMP(purchased_at))", Long.class);
        }

        String officeWallClockText(String ticket) throws SQLException {
            return purchase(ticket, "CAST(office_wall_clock AS CHAR)", String.class);
        }

        private <T> T purchase(String ticket, String expression, Class<T> type) throws SQLException {
            try (Connection connection = office.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT " + expression + " FROM tickets WHERE ticket = ?")) {
                query.setString(1, ticket);
                try (ResultSet row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    return row.getObject(1, type);
                }
            }
        }
    }
}
