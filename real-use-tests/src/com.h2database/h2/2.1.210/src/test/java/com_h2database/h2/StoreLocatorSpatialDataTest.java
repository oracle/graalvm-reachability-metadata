/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.h2.api.ErrorCode;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.db.SpatialKey;
import org.h2.mvstore.rtree.MVRTreeMap;
import org.h2.mvstore.rtree.Spatial;
import org.h2.mvstore.type.StringDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Spatial data: `GEOMETRY` columns with type and SRID constraints, JTS geometries bound and read
 * through JDBC, EWKT literals, spatial indexes queried with the `&&` bounding-box operator, and
 * the MVStore R-tree map `MVRTreeMap` used directly.
 *
 * A store locator keeps shop locations as WGS 84 points and delivery zones as polygons, answers
 * "which shops lie in this map window" and "which zone serves which shop" through a spatial
 * index that survives a database reopen, and keeps a standalone R-tree of parking lots.
 */
public class StoreLocatorSpatialDataTest {

    private static final int WGS84 = 4326;

    private static final GeometryFactory GEOMETRIES = new GeometryFactory(new PrecisionModel(), WGS84);

    @TempDir
    Path directory;

    @Test
    void storesJtsPointsAndReadsThemBackAsGeometries() throws SQLException {
        try (Connection connection = open("points")) {
            createSchema(connection);
            insertShop(connection, 1, "Old Town", point(20.4573, 44.8176));
            insertShop(connection, 2, "Riverside", point(20.4489, 44.8125));

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO SHOP(ID, NAME, LOCATION)
                        VALUES (3, 'Airport', 'SRID=4326;POINT(20.3091 44.8184)')
                        """);
            }

            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT LOCATION, NAME FROM SHOP WHERE ID = ?
                    """)) {
                query.setInt(1, 3);
                try (ResultSet rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    Geometry location = rows.getObject(1, Geometry.class);
                    Point airport = assertInstanceOf(Point.class, location);
                    assertEquals(20.3091, airport.getX(), 1e-9);
                    assertEquals(44.8184, airport.getY(), 1e-9);
                    assertEquals(WGS84, airport.getSRID());
                    assertEquals("Airport", rows.getString(2));

                    ResultSetMetaData metaData = rows.getMetaData();
                    assertEquals("GEOMETRY(POINT, 4326)", metaData.getColumnTypeName(1));
                    assertEquals(Geometry.class.getName(), metaData.getColumnClassName(1));
                }

                query.setInt(1, 1);
                try (ResultSet rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("SRID=4326;POINT (20.4573 44.8176)", rows.getString(1));
                    assertTrue(point(20.4573, 44.8176).equalsExact(rows.getObject(1, Geometry.class)));
                }
            }
        }
    }

    @Test
    void rejectsGeometriesThatBreakTheColumnConstraints() throws SQLException {
        try (Connection connection = open("constraints")) {
            createSchema(connection);
            insertShop(connection, 1, "Old Town", point(20.4573, 44.8176));

            Polygon notAPoint = rectangle(20.40, 44.80, 20.50, 44.83);
            SQLException wrongType = assertThrows(SQLException.class,
                    () -> insertShop(connection, 2, "Kiosk", notAPoint));
            assertEquals(ErrorCode.DATA_CONVERSION_ERROR_1, wrongType.getErrorCode(), wrongType::getMessage);

            Point mercator = new GeometryFactory(new PrecisionModel(), 3857)
                    .createPoint(new Coordinate(2_277_284.0, 5_594_870.0));
            SQLException wrongSrid = assertThrows(SQLException.class,
                    () -> insertShop(connection, 3, "Elsewhere", mercator));
            assertEquals(ErrorCode.DATA_CONVERSION_ERROR_1, wrongSrid.getErrorCode(), wrongSrid::getMessage);

            try (Statement statement = connection.createStatement()) {
                SQLException badText = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                        INSERT INTO SHOP(ID, NAME, LOCATION) VALUES (4, 'Typo', 'POINT(20.1')
                        """));
                assertEquals(ErrorCode.DATA_CONVERSION_ERROR_1, badText.getErrorCode(), badText::getMessage);

                try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM SHOP")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
            }
        }
    }

    @Test
    void findsShopsInMapWindowThroughSpatialIndexAcrossReopen() throws SQLException {
        try (Connection connection = open("locator")) {
            createSchema(connection);
            insertShop(connection, 1, "Old Town", point(20.4573, 44.8176));
            insertShop(connection, 2, "Riverside", point(20.4489, 44.8125));
            insertShop(connection, 3, "Airport", point(20.3091, 44.8184));
            insertShop(connection, 4, "New Belgrade", point(20.4120, 44.8050));
            insertShop(connection, 5, "Zemun", point(20.4010, 44.8430));

            Polygon cityCentre = rectangle(20.44, 44.80, 20.47, 44.83);
            assertEquals(List.of("Old Town", "Riverside"), shopsInWindow(connection, cityCentre));
            assertTrue(explainWindowQuery(connection).contains("SHOP_LOCATION"));

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        UPDATE SHOP SET LOCATION = 'SRID=4326;POINT(20.4600 44.8150)' WHERE NAME = 'Zemun'
                        """);
                statement.executeUpdate("DELETE FROM SHOP WHERE NAME = 'Riverside'");
            }
            assertEquals(List.of("Old Town", "Zemun"), shopsInWindow(connection, cityCentre));
        }

        try (Connection reopened = open("locator")) {
            Polygon wholeCity = rectangle(20.30, 44.79, 20.47, 44.85);
            assertEquals(List.of("Old Town", "Airport", "New Belgrade", "Zemun"), shopsInWindow(reopened, wholeCity));
            assertEquals(List.of(), shopsInWindow(reopened, rectangle(21.0, 45.0, 21.1, 45.1)));
            assertTrue(explainWindowQuery(reopened).contains("SHOP_LOCATION"));
        }
    }

    @Test
    void joinsShopsToTheDeliveryZonesWhoseBoundsCoverThem() throws SQLException {
        try (Connection connection = open("zones")) {
            createSchema(connection);
            insertShop(connection, 1, "Old Town", point(20.4573, 44.8176));
            insertShop(connection, 3, "Airport", point(20.3091, 44.8184));
            insertShop(connection, 5, "Zemun", point(20.4010, 44.8430));

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE ZONE(
                            CODE VARCHAR(8) PRIMARY KEY,
                            AREA GEOMETRY(POLYGON, 4326) NOT NULL)
                        """);
                statement.execute("CREATE SPATIAL INDEX ZONE_AREA ON ZONE(AREA)");
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO ZONE VALUES (?, ?)")) {
                insert.setString(1, "CENTRE");
                insert.setObject(2, rectangle(20.43, 44.80, 20.48, 44.83));
                insert.addBatch();
                insert.setString(1, "WEST");
                insert.setObject(2, rectangle(20.28, 44.79, 20.42, 44.86));
                insert.addBatch();
                assertEquals(2, insert.executeBatch().length);
            }

            List<String> assignments = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("""
                            SELECT Z.CODE, S.NAME, Z.AREA
                            FROM ZONE Z JOIN SHOP S ON Z.AREA && S.LOCATION
                            ORDER BY Z.CODE, S.ID
                            """)) {
                while (rows.next()) {
                    assignments.add(rows.getString(1) + ":" + rows.getString(2));
                    Polygon area = assertInstanceOf(Polygon.class, rows.getObject(3, Geometry.class));
                    assertEquals(WGS84, area.getSRID());
                    assertFalse(area.isEmpty());
                }
            }
            assertEquals(List.of("CENTRE:Old Town", "WEST:Airport", "WEST:Zemun"), assignments);
        }
    }

    @Test
    void keepsParkingLotsInStandaloneRTreeMap() {
        String file = directory.resolve("parking.mv.db").toString();
        MVRTreeMap.Builder<String> lots = new MVRTreeMap.Builder<String>()
                .dimensions(2)
                .valueType(StringDataType.INSTANCE);

        try (MVStore store = new MVStore.Builder().fileName(file).open()) {
            MVRTreeMap<String> parking = store.openMap("parking", lots);
            parking.add(box(1, 20.450f, 44.815f, 20.452f, 44.817f), "Republic Square garage");
            parking.add(box(2, 20.447f, 44.811f, 20.449f, 44.813f), "Riverside lot");
            parking.add(box(3, 20.305f, 44.816f, 20.312f, 44.821f), "Airport long-stay");
            parking.add(box(4, 20.440f, 44.800f, 20.470f, 44.830f), "City park-and-ride");
            assertEquals(4, parking.size());
            store.commit();
        }

        try (MVStore store = new MVStore.Builder().fileName(file).readOnly().open()) {
            MVRTreeMap<String> parking = store.openMap("parking", lots);
            Spatial walkingDistance = box(0, 20.446f, 44.810f, 20.453f, 44.818f);

            Set<String> touching = valuesOf(parking, parking.findIntersectingKeys(walkingDistance));
            assertEquals(Set.of("City park-and-ride", "Republic Square garage", "Riverside lot"), touching);

            Set<String> inside = valuesOf(parking, parking.findContainedKeys(walkingDistance));
            assertEquals(Set.of("Republic Square garage", "Riverside lot"), inside);

            Set<String> nowhere = valuesOf(parking, parking.findIntersectingKeys(box(0, 0f, 0f, 1f, 1f)));
            assertTrue(nowhere.isEmpty());
        }
    }

    private Connection open(String name) throws SQLException {
        return DriverManager.getConnection("jdbc:h2:" + directory.resolve(name), "sa", "");
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE SHOP(
                        ID INT PRIMARY KEY,
                        NAME VARCHAR(40) NOT NULL,
                        LOCATION GEOMETRY(POINT, 4326) NOT NULL)
                    """);
            statement.execute("CREATE SPATIAL INDEX SHOP_LOCATION ON SHOP(LOCATION)");
        }
    }

    private static void insertShop(Connection connection, int id, String name, Geometry location)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO SHOP(ID, NAME, LOCATION) VALUES (?, ?, ?)
                """)) {
            insert.setInt(1, id);
            insert.setString(2, name);
            insert.setObject(3, location);
            assertEquals(1, insert.executeUpdate());
        }
    }

    private static List<String> shopsInWindow(Connection connection, Polygon window) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT NAME FROM SHOP WHERE LOCATION && ? ORDER BY ID
                """)) {
            query.setObject(1, window);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    names.add(rows.getString(1));
                }
            }
        }
        return names;
    }

    private static String explainWindowQuery(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet plan = statement.executeQuery("""
                        EXPLAIN SELECT NAME FROM SHOP
                        WHERE LOCATION && CAST('SRID=4326;POLYGON((20 44, 21 44, 21 45, 20 45, 20 44))' AS GEOMETRY)
                        """)) {
            assertTrue(plan.next());
            return plan.getString(1);
        }
    }

    private static Point point(double longitude, double latitude) {
        return GEOMETRIES.createPoint(new Coordinate(longitude, latitude));
    }

    private static Polygon rectangle(double minX, double minY, double maxX, double maxY) {
        return (Polygon) GEOMETRIES.toGeometry(new Envelope(minX, maxX, minY, maxY));
    }

    private static Spatial box(long id, float minX, float minY, float maxX, float maxY) {
        return new SpatialKey(id, minX, maxX, minY, maxY);
    }

    private static Set<String> valuesOf(MVRTreeMap<String> map, MVRTreeMap.RTreeCursor<String> cursor) {
        Set<String> values = new TreeSet<>();
        while (cursor.hasNext()) {
            values.add(map.get(cursor.next()));
        }
        return values;
    }
}
