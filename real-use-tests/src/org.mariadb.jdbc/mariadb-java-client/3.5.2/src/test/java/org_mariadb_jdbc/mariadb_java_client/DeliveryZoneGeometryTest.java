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
import org.mariadb.jdbc.type.Geometry;
import org.mariadb.jdbc.type.GeometryCollection;
import org.mariadb.jdbc.type.LineString;
import org.mariadb.jdbc.type.MultiLineString;
import org.mariadb.jdbc.type.MultiPoint;
import org.mariadb.jdbc.type.MultiPolygon;
import org.mariadb.jdbc.type.Point;
import org.mariadb.jdbc.type.Polygon;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/// Spatial values through the driver's `org.mariadb.jdbc.type` geometry classes: `Point`,
/// `LineString`, `Polygon` and the multi-geometry and collection types bound with
/// `setObject`, read back with `getObject`, and used as parameters of spatial predicates,
/// over both the text and the binary protocol, with `geometryDefaultType=default` turning
/// untyped `getObject` reads of spatial columns into driver geometry objects.
///
/// The scenario is a delivery-zone service of a courier company that keeps depots,
/// delivery zones and driven routes in MariaDB spatial columns and asks the server which
/// zone serves an address, against a real MariaDB server.
public class DeliveryZoneGeometryTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-geometry-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String DATABASE = "courier";

    private static final String USER = "dispatcher";

    private static final String PASSWORD = "dispatcher-secret";

    private static String url;

    private static MariaDbDataSource textSource;

    private static MariaDbDataSource binarySource;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_DATABASE=" + DATABASE,
                "-e", "MARIADB_USER=" + USER,
                "-e", "MARIADB_PASSWORD=" + PASSWORD,
                "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true",
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        url = "jdbc:mariadb://127.0.0.1:" + port + "/" + DATABASE;

        textSource = dataSource("useServerPrepStmts=false&geometryDefaultType=default");
        binarySource = dataSource("useServerPrepStmts=true&geometryDefaultType=default");
        awaitServer(textSource);

        try (Connection connection = textSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE depot (
                        name VARCHAR(40) NOT NULL PRIMARY KEY,
                        location POINT NOT NULL
                    ) ENGINE=InnoDB""");
            statement.execute("""
                    CREATE TABLE delivery_zone (
                        code VARCHAR(10) NOT NULL PRIMARY KEY,
                        area POLYGON NOT NULL
                    ) ENGINE=InnoDB""");
            statement.execute("""
                    CREATE TABLE route (
                        id INT NOT NULL PRIMARY KEY,
                        path LINESTRING NULL
                    ) ENGINE=InnoDB""");
            statement.execute("""
                    CREATE TABLE coverage (
                        label VARCHAR(40) NOT NULL PRIMARY KEY,
                        shape GEOMETRY NOT NULL
                    ) ENGINE=InnoDB""");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @BeforeEach
    void clearTables() throws SQLException {
        try (Connection connection = textSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM depot");
            statement.execute("DELETE FROM delivery_zone");
            statement.execute("DELETE FROM route");
            statement.execute("DELETE FROM coverage");
        }
    }

    @Test
    void depotsKeepTheirCoordinatesOverBothProtocols() throws SQLException {
        ZoneService textService = new ZoneService(textSource);
        ZoneService binaryService = new ZoneService(binarySource);
        textService.addDepot("north", new Point(10.5, 20.25));
        binaryService.addDepot("south", new Point(-3.125, 7.0));

        for (ZoneService service : List.of(textService, binaryService)) {
            Point north = service.depot("north");
            Point south = service.depot("south");
            assertThat(north).isEqualTo(new Point(10.5, 20.25));
            assertThat(south.getX()).isEqualTo(-3.125);
            assertThat(south.getY()).isEqualTo(7.0);
        }

        try (Connection connection = textSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT ST_X(location), ST_Y(location), ST_AsText(location)
                        FROM depot WHERE name = 'north'""")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getDouble(1)).isEqualTo(10.5);
            assertThat(rows.getDouble(2)).isEqualTo(20.25);
            assertThat(rows.getString(3)).isEqualTo("POINT(10.5 20.25)");
        }
    }

    @Test
    void addressesAreRoutedToTheZoneThatContainsThem() throws SQLException {
        ZoneService service = new ZoneService(binarySource);
        service.addZone("CENTER", square(0, 0, 10));
        service.addZone("EAST", square(10, 0, 10));

        assertThat(service.zoneServing(new Point(3, 4))).isEqualTo("CENTER");
        assertThat(service.zoneServing(new Point(15, 2))).isEqualTo("EAST");
        assertThat(service.zoneServing(new Point(50, 50))).isNull();

        Polygon center = service.zone("CENTER");
        assertThat(center).isEqualTo(square(0, 0, 10));
        assertThat(center.getLines()).hasSize(1);
        assertThat(center.getLines()[0].getPoints()).hasSize(5);
        assertThat(center.getLines()[0].isOpen()).isFalse();
        assertThat(service.zoneArea("EAST")).isEqualTo(100.0);
    }

    @Test
    void routesAreStoredAsLineStringsAndMeasuredByTheServer() throws SQLException {
        ZoneService textService = new ZoneService(textSource);
        LineString drive = new LineString(new Point[] {
            new Point(0, 0), new Point(3, 4), new Point(3, 10)
        }, true);
        textService.recordRoute(1, drive);
        textService.recordRoute(2, null);

        ZoneService binaryService = new ZoneService(binarySource);
        LineString stored = binaryService.route(1);
        assertThat(stored).isEqualTo(drive);
        assertThat(stored.isOpen()).isTrue();
        assertThat(stored.getPoints()).extracting(Point::getY).containsExactly(0.0, 4.0, 10.0);
        assertThat(binaryService.route(2)).isNull();
        assertThat(binaryService.routeLength(1)).isCloseTo(11.0, within(1e-9));
    }

    @Test
    void coverageShapesComeBackAsTheirOwnGeometryKinds() throws SQLException {
        ZoneService service = new ZoneService(binarySource);
        MultiPoint lockers = new MultiPoint(new Point[] {new Point(1, 1), new Point(2, 3)});
        MultiLineString corridors = new MultiLineString(new LineString[] {
            new LineString(new Point[] {new Point(0, 0), new Point(0, 5)}, true),
            new LineString(new Point[] {new Point(1, 0), new Point(1, 5)}, true)
        });
        MultiPolygon islands = new MultiPolygon(new Polygon[] {square(0, 0, 2), square(5, 5, 1)});
        GeometryCollection mixed = new GeometryCollection(new Geometry[] {
            new Point(7, 7), new LineString(new Point[] {new Point(0, 0), new Point(2, 2)}, true)
        });
        service.addCoverage("lockers", lockers);
        service.addCoverage("corridors", corridors);
        service.addCoverage("islands", islands);
        service.addCoverage("mixed", mixed);

        for (MariaDbDataSource source : List.of(textSource, binarySource)) {
            ZoneService reader = new ZoneService(source);
            assertThat(reader.coverage("lockers")).isInstanceOf(MultiPoint.class).isEqualTo(lockers);
            assertThat(reader.coverage("corridors")).isInstanceOf(MultiLineString.class).isEqualTo(corridors);
            assertThat(reader.coverage("islands")).isInstanceOf(MultiPolygon.class).isEqualTo(islands);
            assertThat(reader.coverage("mixed")).isInstanceOf(GeometryCollection.class).isEqualTo(mixed);
        }

        GeometryCollection read = (GeometryCollection) service.coverage("mixed");
        assertThat(read.getGeometries()[0]).isEqualTo(new Point(7, 7));
        assertThat(((MultiPolygon) service.coverage("islands")).getPolygons()).hasSize(2);
        assertThat(service.coverageArea("islands")).isEqualTo(5.0);
        assertThat(service.coverageKind("corridors")).isEqualTo("MULTILINESTRING");
    }

    @Test
    void withoutAGeometryDefaultTypeUntypedReadsReturnTheRawBytes() throws SQLException {
        new ZoneService(textSource).addDepot("west", new Point(1.5, 2.5));

        ZoneService legacyReader = new ZoneService(dataSource("useServerPrepStmts=true"));
        Object raw = legacyReader.single("SELECT location FROM depot WHERE name = ?", "west");
        assertThat(raw).isInstanceOf(byte[].class);
        assertThat((byte[]) raw).isNotEmpty();
        assertThat(legacyReader.depot("west")).isEqualTo(new Point(1.5, 2.5));
    }

    private static Polygon square(double x, double y, double side) {
        return new Polygon(new LineString[] {
            new LineString(new Point[] {
                new Point(x, y),
                new Point(x + side, y),
                new Point(x + side, y + side),
                new Point(x, y + side),
                new Point(x, y)
            }, false)
        });
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

    /// Courier back office that stores depots, zones, routes and coverage shapes.
    static final class ZoneService {

        private final MariaDbDataSource source;

        ZoneService(MariaDbDataSource source) {
            this.source = source;
        }

        void addDepot(String name, Point location) throws SQLException {
            update("INSERT INTO depot (name, location) VALUES (?, ?)", name, location);
        }

        Point depot(String name) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT location FROM depot WHERE name = ?")) {
                statement.setString(1, name);
                try (ResultSet rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getObject(1, Point.class);
                }
            }
        }

        void addZone(String code, Polygon area) throws SQLException {
            update("INSERT INTO delivery_zone (code, area) VALUES (?, ?)", code, area);
        }

        Polygon zone(String code) throws SQLException {
            return (Polygon) single("SELECT area FROM delivery_zone WHERE code = ?", code);
        }

        /// Returns the code of the zone whose polygon contains the address, or `null`.
        String zoneServing(Point address) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT code FROM delivery_zone WHERE ST_Contains(area, ?)")) {
                statement.setObject(1, address);
                try (ResultSet rows = statement.executeQuery()) {
                    String code = rows.next() ? rows.getString(1) : null;
                    assertThat(rows.next()).isFalse();
                    return code;
                }
            }
        }

        double zoneArea(String code) throws SQLException {
            return (Double) single("SELECT ST_Area(area) FROM delivery_zone WHERE code = ?", code);
        }

        void recordRoute(int id, LineString path) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO route (id, path) VALUES (?, ?)")) {
                statement.setInt(1, id);
                if (path == null) {
                    statement.setNull(2, Types.OTHER);
                } else {
                    statement.setObject(2, path);
                }
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
        }

        LineString route(int id) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT path FROM route WHERE id = ?")) {
                statement.setInt(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getObject(1, LineString.class);
                }
            }
        }

        double routeLength(int id) throws SQLException {
            return (Double) single("SELECT ST_Length(path) FROM route WHERE id = ?", id);
        }

        void addCoverage(String label, Geometry shape) throws SQLException {
            update("INSERT INTO coverage (label, shape) VALUES (?, ?)", label, shape);
        }

        Geometry coverage(String label) throws SQLException {
            return (Geometry) single("SELECT shape FROM coverage WHERE label = ?", label);
        }

        double coverageArea(String label) throws SQLException {
            return (Double) single("SELECT ST_Area(shape) FROM coverage WHERE label = ?", label);
        }

        String coverageKind(String label) throws SQLException {
            return (String) single("SELECT ST_GeometryType(shape) FROM coverage WHERE label = ?", label);
        }

        private void update(String sql, Object... parameters) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
        }

        private Object single(String sql, Object key) throws SQLException {
            try (Connection connection = source.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, key);
                try (ResultSet rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    Object value = rows.getObject(1);
                    assertThat(rows.next()).isFalse();
                    return value;
                }
            }
        }
    }
}
