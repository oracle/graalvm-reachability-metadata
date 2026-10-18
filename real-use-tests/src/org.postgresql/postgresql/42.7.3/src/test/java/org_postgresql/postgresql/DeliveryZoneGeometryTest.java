/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.geometric.PGbox;
import org.postgresql.geometric.PGcircle;
import org.postgresql.geometric.PGlseg;
import org.postgresql.geometric.PGpath;
import org.postgresql.geometric.PGpoint;
import org.postgresql.geometric.PGpolygon;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Plans courier deliveries with PostgreSQL geometric columns bound and read through the driver's
 * `org.postgresql.geometric` types (`PGpoint`, `PGcircle`, `PGpolygon`, `PGbox`, `PGpath`, `PGlseg`).
 *
 * A dispatcher stores depots as points, service zones as circles and polygons, and courier routes
 * as paths; it then asks the server which zones cover a drop-off, which depot is nearest, the
 * leg from that depot to the drop-off, the bounding box of a zone and the length of a route,
 * reading every answer back as a typed value.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeliveryZoneGeometryTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String DATABASE = "dispatch";

    private static final String USER = "dispatcher";

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
            statement.execute("CREATE TABLE depots (name text PRIMARY KEY, location point NOT NULL)");
            statement.execute("""
                    CREATE TABLE zones (
                        name     text PRIMARY KEY,
                        radius   circle,
                        boundary polygon
                    )""");
            statement.execute("CREATE TABLE routes (courier text PRIMARY KEY, track path NOT NULL)");
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void plansDeliveriesAgainstStoredGeometry() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO depots (name, location) VALUES (?, ?)")) {
                insertDepot(insert, "north", new PGpoint(2.0, 9.0));
                insertDepot(insert, "harbor", new PGpoint(8.5, 1.5));
                assertThat(insert.executeBatch()).containsExactly(1, 1);
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO zones (name, radius, boundary) VALUES (?, ?, ?)")) {
                insert.setString(1, "downtown");
                insert.setObject(2, new PGcircle(5.0, 5.0, 3.0));
                insert.setObject(3, null);
                insert.addBatch();
                insert.setString(1, "industrial");
                insert.setObject(2, null);
                insert.setObject(3, new PGpolygon(new PGpoint[] {
                    new PGpoint(6.0, 0.0), new PGpoint(10.0, 0.0), new PGpoint(10.0, 4.0), new PGpoint(6.0, 4.0)
                }));
                insert.addBatch();
                assertThat(insert.executeBatch()).containsExactly(1, 1);
            }

            PGpath plannedTrack = new PGpath(new PGpoint[] {
                new PGpoint(2.0, 9.0), new PGpoint(5.0, 5.0), new PGpoint(8.0, 2.0)
            }, true);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO routes (courier, track) VALUES (?, ?)")) {
                insert.setString(1, "ana");
                insert.setObject(2, plannedTrack);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }

            PGpoint dropOff = new PGpoint(7.0, 3.0);
            try (PreparedStatement covering = connection.prepareStatement("""
                    SELECT name FROM zones
                    WHERE radius @> ?::point OR boundary @> ?::point
                    ORDER BY name""")) {
                covering.setObject(1, dropOff);
                covering.setObject(2, dropOff);
                List<String> zones = new ArrayList<>();
                try (ResultSet rows = covering.executeQuery()) {
                    while (rows.next()) {
                        zones.add(rows.getString("name"));
                    }
                }
                assertThat(zones).containsExactly("downtown", "industrial");
            }

            try (PreparedStatement nearest = connection.prepareStatement("""
                    SELECT name, location, location <-> ? AS distance, lseg(location, ?) AS leg
                    FROM depots ORDER BY distance LIMIT 1""")) {
                nearest.setObject(1, dropOff);
                nearest.setObject(2, dropOff);
                try (ResultSet rows = nearest.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("name")).isEqualTo("harbor");
                    assertThat(rows.getObject("location")).isEqualTo(new PGpoint(8.5, 1.5));
                    assertThat(rows.getDouble("distance")).isCloseTo(Math.hypot(1.5, 1.5), within(1e-9));
                    PGlseg leg = (PGlseg) rows.getObject("leg");
                    assertThat(leg.point).containsExactly(new PGpoint(8.5, 1.5), dropOff);
                }
            }

            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("""
                            SELECT radius, box(boundary) AS bounds
                            FROM zones ORDER BY name""")) {
                assertThat(rows.next()).isTrue();
                PGcircle downtown = rows.getObject("radius", PGcircle.class);
                assertThat(downtown.center).isEqualTo(new PGpoint(5.0, 5.0));
                assertThat(downtown.radius).isEqualTo(3.0);
                assertThat(rows.getObject("bounds")).isNull();

                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("radius")).isNull();
                Object bounds = rows.getObject("bounds");
                assertThat(bounds).isInstanceOf(PGbox.class);
                PGbox industrialBounds = (PGbox) bounds;
                assertThat(industrialBounds).isEqualTo(new PGbox(6.0, 0.0, 10.0, 4.0));
                assertThat(industrialBounds.getType()).isEqualTo("box");
                assertThat(rows.next()).isFalse();
            }

            try (PreparedStatement route = connection.prepareStatement("""
                    SELECT track, npoints(track) AS stops, length(track) AS distance
                    FROM routes WHERE courier = ?""")) {
                route.setString(1, "ana");
                try (ResultSet rows = route.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    PGpath stored = (PGpath) rows.getObject("track");
                    assertThat(stored.isOpen()).isTrue();
                    assertThat(stored.points).containsExactly(plannedTrack.points);
                    assertThat(stored).isEqualTo(plannedTrack);

                    assertThat(rows.getInt("stops")).isEqualTo(3);
                    assertThat(rows.getDouble("distance")).isCloseTo(5.0 + Math.hypot(3.0, 3.0), within(1e-9));
                }
            }
        }
    }

    private static void insertDepot(PreparedStatement insert, String name, PGpoint location) throws SQLException {
        insert.setString(1, name);
        insert.setObject(2, location);
        insert.addBatch();
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
