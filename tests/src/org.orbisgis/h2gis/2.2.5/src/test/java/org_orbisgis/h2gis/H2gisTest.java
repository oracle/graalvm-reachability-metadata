/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_orbisgis.h2gis;

import org.h2gis.functions.factory.H2GISFunctions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class H2gisTest {

    @Test
    void loadsSpatialExtensionAndPublishesGeometryMetadata() throws Exception {
        try (Connection connection = openSpatialDatabase("extension_metadata");
                Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT H2GISVersion(), JTSVersion()")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isNotBlank();
                assertThat(resultSet.getString(2)).isNotBlank();
                assertThat(resultSet.next()).isFalse();
            }

            statement.executeUpdate("""
                    CREATE TABLE parcels (
                        id INTEGER PRIMARY KEY,
                        shape GEOMETRY(POLYGON, 4326) NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO parcels(id, shape)
                    VALUES (1, ST_GeomFromText(
                        'POLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))', 4326))
                    """);

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT geometry_type, coord_dimension, srid, type
                    FROM geometry_columns
                    WHERE f_table_schema = 'PUBLIC'
                      AND f_table_name = 'PARCELS'
                      AND f_geometry_column = 'SHAPE'
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt("geometry_type")).isEqualTo(3);
                assertThat(resultSet.getInt("coord_dimension")).isEqualTo(2);
                assertThat(resultSet.getInt("srid")).isEqualTo(4326);
                assertThat(resultSet.getString("type")).isEqualTo("POLYGON");
                assertThat(resultSet.next()).isFalse();
            }

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT COUNT(*) FROM spatial_ref_sys WHERE srid = 4326")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void storesAndReadsTypedGeometriesThroughJdbc() throws Exception {
        try (Connection connection = openSpatialDatabase("typed_geometry_jdbc");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE landmarks (
                        id INTEGER PRIMARY KEY,
                        name VARCHAR(64) NOT NULL,
                        location GEOMETRY(POINT, 4326) NOT NULL
                    )
                    """);

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO landmarks(id, name, location)
                    VALUES (?, ?, ST_GeomFromText(?, ?))
                    """)) {
                insert.setInt(1, 7);
                insert.setString(2, "observatory");
                insert.setString(3, "POINT (2 3)");
                insert.setInt(4, 4326);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }

            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT name,
                           location,
                           ST_AsText(location) AS wkt,
                           ST_X(location) AS x,
                           ST_Y(location) AS y,
                           ST_SRID(location) AS srid,
                           ST_GeometryType(location) AS geometry_type,
                           ST_AsGeoJSON(location) AS geo_json,
                           ST_AsBinary(location) AS wkb
                    FROM landmarks
                    WHERE id = ?
                    """)) {
                query.setInt(1, 7);
                try (ResultSet resultSet = query.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("name")).isEqualTo("observatory");

                    Geometry geometry = resultSet.getObject("location", Geometry.class);
                    assertThat(geometry).isInstanceOf(Point.class);
                    assertThat(geometry.getSRID()).isEqualTo(4326);
                    assertThat(geometry.getCoordinate().getX()).isEqualTo(2.0);
                    assertThat(geometry.getCoordinate().getY()).isEqualTo(3.0);

                    assertThat(resultSet.getString("wkt")).isEqualTo("POINT (2 3)");
                    assertThat(resultSet.getDouble("x")).isEqualTo(2.0);
                    assertThat(resultSet.getDouble("y")).isEqualTo(3.0);
                    assertThat(resultSet.getInt("srid")).isEqualTo(4326);
                    assertThat(resultSet.getString("geometry_type")).isEqualTo("POINT");
                    assertThat(resultSet.getString("geo_json")).contains("\"type\":\"Point\"");
                    assertThat(resultSet.getBytes("wkb")).isNotEmpty();
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }

    @Test
    void evaluatesSpatialMeasurementsPredicatesAndOperators() throws Exception {
        try (Connection connection = openSpatialDatabase("spatial_operators");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT
                            ST_Area(ST_GeomFromText(
                                'POLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))')) AS area,
                            ST_Length(ST_Boundary(ST_GeomFromText(
                                'POLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))'))) AS perimeter,
                            ST_Contains(
                                ST_GeomFromText('POLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))'),
                                ST_GeomFromText('POINT (1 1)')) AS contains_point,
                            ST_Intersects(
                                ST_GeomFromText('LINESTRING (0 0, 4 4)'),
                                ST_GeomFromText('LINESTRING (0 4, 4 0)')) AS intersects_lines,
                            ST_Distance(
                                ST_GeomFromText('POINT (0 0)'),
                                ST_GeomFromText('POINT (3 4)')) AS distance,
                            ST_Area(ST_Intersection(
                                ST_GeomFromText('POLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))'),
                                ST_GeomFromText('POLYGON ((2 1, 5 1, 5 2, 2 2, 2 1))'))) AS overlap_area,
                            ST_Area(ST_Buffer(ST_GeomFromText('POINT (0 0)'), 1)) AS buffer_area,
                            ST_GeometryType(ST_ConvexHull(ST_GeomFromText(
                                'MULTIPOINT ((0 0), (2 0), (1 2))'))) AS hull_type
                        """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getDouble("area")).isEqualTo(12.0);
            assertThat(resultSet.getDouble("perimeter")).isEqualTo(14.0);
            assertThat(resultSet.getBoolean("contains_point")).isTrue();
            assertThat(resultSet.getBoolean("intersects_lines")).isTrue();
            assertThat(resultSet.getDouble("distance")).isEqualTo(5.0);
            assertThat(resultSet.getDouble("overlap_area")).isEqualTo(2.0);
            assertThat(resultSet.getDouble("buffer_area")).isBetween(3.0, 4.0);
            assertThat(resultSet.getString("hull_type")).isEqualTo("POLYGON");
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void evaluatesSpatialAggregatesCoordinateTransformsAndConversions() throws Exception {
        try (Connection connection = openSpatialDatabase("aggregate_transform_conversion");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE samples (id INTEGER PRIMARY KEY, geom GEOMETRY(POINT, 4326))");
            statement.executeUpdate("""
                    INSERT INTO samples(id, geom) VALUES
                        (1, ST_GeomFromText('POINT (0 0)', 4326)),
                        (2, ST_GeomFromText('POINT (2 0)', 4326)),
                        (3, ST_GeomFromText('POINT (2 2)', 4326))
                    """);

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT ST_NumGeometries(ST_Accum(geom)) AS geometry_count,
                           ST_Area(ST_Extent(geom)) AS extent_area
                    FROM samples
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt("geometry_count")).isEqualTo(3);
                assertThat(resultSet.getDouble("extent_area")).isEqualTo(4.0);
                assertThat(resultSet.next()).isFalse();
            }

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT ST_SRID(projected) AS projected_srid,
                           ST_X(projected) AS projected_x,
                           ST_Y(projected) AS projected_y,
                           ST_AsText(ST_GeomFromGeoJSON(
                               '{"type":"LineString","coordinates":[[0,0],[2,2]]}')) AS geo_json_wkt,
                           ST_AsText(ST_Translate(
                               ST_GeomFromText('POINT (1 2)'), 3, 4)) AS translated_wkt,
                           ST_Length(ST_ShortestLine(
                               ST_GeomFromText('POINT (0 0)'),
                               ST_GeomFromText('POINT (6 8)'))) AS shortest_line_length
                    FROM (SELECT ST_Transform(
                        ST_GeomFromText('POINT (0 0)', 4326), 3857) AS projected) AS transformed
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt("projected_srid")).isEqualTo(3857);
                assertThat(resultSet.getDouble("projected_x")).isCloseTo(0.0, within(0.001));
                assertThat(resultSet.getDouble("projected_y")).isCloseTo(0.0, within(0.001));
                assertThat(resultSet.getString("geo_json_wkt")).isEqualTo("LINESTRING (0 0, 2 2)");
                assertThat(resultSet.getString("translated_wkt")).isEqualTo("POINT (4 6)");
                assertThat(resultSet.getDouble("shortest_line_length")).isEqualTo(10.0);
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    private static Connection openSpatialDatabase(String name) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + name);
        try {
            H2GISFunctions.load(connection);
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }
}
