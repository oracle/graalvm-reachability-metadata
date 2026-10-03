/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_orbisgis.h2gis_utilities;

import org.h2gis.functions.factory.H2GISFunctions;
import org.h2gis.utilities.GeometryMetaData;
import org.h2gis.utilities.GeometryTableUtilities;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.SpatialResultSet;
import org.h2gis.utilities.TableLocation;
import org.h2gis.utilities.Tuple;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.h2gis.utilities.jts_utils.GeometryFeatureUtils;
import org.h2gis.utilities.wrapper.SpatialResultSetImpl;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class H2gis_utilitiesTest {

    @Test
    void parsesAndFormatsTableLocations() throws Exception {
        try (Connection connection = openSpatialDatabase("table_location")) {
            TableLocation location = TableLocation.parse("catalog.public.places", DBTypes.H2GIS);

            assertThat(location.getCatalog()).isEqualTo("CATALOG");
            assertThat(location.getSchema()).isEqualTo("PUBLIC");
            assertThat(location.getTable()).isEqualTo("PLACES");
            assertThat(location.toString()).isEqualTo("CATALOG.PUBLIC.PLACES");
            assertThat(location.toString(DBTypes.H2GIS)).isEqualTo("CATALOG.PUBLIC.PLACES");
            assertThat(TableLocation.split("catalog.public.places"))
                    .containsExactly("catalog", "public", "places");
            assertThat(TableLocation.quoteIdentifier("name\"value"))
                    .isEqualTo("\"name\"\"value\"");
            assertThat(TableLocation.capsIdentifier("places", DBTypes.H2GIS)).isEqualTo("PLACES");
        }
    }

    @Test
    void inspectsSpatialTableWithJdbcUtilities() throws Exception {
        try (Connection connection = openSpatialDatabase("jdbc_utilities");
                Statement statement = connection.createStatement()) {
            createSpatialTable(connection);
            TableLocation table = new TableLocation("PUBLIC", "PLACES", DBTypes.H2);

            assertThat(JDBCUtilities.isH2DataBase(connection)).isTrue();
            assertThat(JDBCUtilities.tableExists(connection, table)).isTrue();
            assertThat(JDBCUtilities.tableExists(connection, "PUBLIC.MISSING_PLACES")).isFalse();
            assertThat(JDBCUtilities.getTableType(connection, table))
                    .isEqualTo(JDBCUtilities.TABLE_TYPE.TABLE);
            assertThat(JDBCUtilities.hasField(connection, table, "GEOM")).isTrue();
            assertThat(JDBCUtilities.hasField(connection, "PUBLIC.PLACES", "missing")).isFalse();
            assertThat(JDBCUtilities.getColumnNames(connection, table))
                    .containsExactly("ID", "NAME", "AMOUNT", "GEOM", "AREA");
            assertThat(JDBCUtilities.getColumnName(connection, table, 4)).isEqualTo("GEOM");
            assertThat(JDBCUtilities.getRowCount(connection, table)).isEqualTo(3);
            assertThat(JDBCUtilities.getUniqueFieldValues(connection, table, "NAME"))
                    .containsExactlyInAnyOrder("north", "south", "east");
            assertThat(JDBCUtilities.getNumericColumns(connection, table))
                    .containsExactly("ID", "AMOUNT");
            assertThat(JDBCUtilities.getFirstNumericColumn(connection, table)).isEqualTo("ID");
            assertThat(JDBCUtilities.getFirstAutoIncrementColumn(connection, table)).isEqualTo("ID");

            List<Tuple<String, Integer>> columns = JDBCUtilities.getColumnNamesAndIndexes(connection, table);
            assertThat(columns).hasSize(5);
            assertThat(columns.get(0).first()).isEqualTo("ID");
            assertThat(columns.get(0).second()).isEqualTo(1);
            assertThat(columns.get(3).first()).isEqualTo("GEOM");
            assertThat(columns.get(3).second()).isEqualTo(4);

            List<String> tables = JDBCUtilities.getTableNames(
                    connection, table, new String[]{"TABLE"});
            assertThat(tables).anyMatch(tableName -> tableName.endsWith(".PUBLIC.PLACES"));
            Tuple<String, Integer> primaryKey =
                    JDBCUtilities.getIntegerPrimaryKeyNameAndIndex(connection, table);
            assertThat(primaryKey.first()).isEqualTo("ID");
            assertThat(primaryKey.second()).isEqualTo(1);
            assertThat(JDBCUtilities.getIntegerPrimaryKey(connection, table)).isEqualTo(1);

            assertThat(JDBCUtilities.createTableDDL(
                    connection, table, new TableLocation("PUBLIC", "PLACES_COPY", DBTypes.H2)))
                    .contains("CREATE TABLE PUBLIC.PLACES_COPY")
                    .contains("GEOM GEOMETRY")
                    .contains("NAME CHARACTER VARYING");

            try (ResultSet resultSet = statement.executeQuery("SELECT * FROM PUBLIC.PLACES LIMIT 0")) {
                assertThat(JDBCUtilities.getFieldIndex(resultSet.getMetaData(), "amount")).isEqualTo(3);
                assertThat(JDBCUtilities.getColumnName(resultSet.getMetaData(), 5)).isEqualTo("AREA");
                assertThat(JDBCUtilities.getColumnNames(resultSet.getMetaData()))
                        .containsExactly("ID", "NAME", "AMOUNT", "GEOM", "AREA");
            }
        }
    }

    @Test
    void readsGeometryMetadataAndExtentsFromSpatialCatalog() throws Exception {
        try (Connection connection = openSpatialDatabase("geometry_utilities")) {
            createSpatialTable(connection);
            TableLocation table = new TableLocation("PUBLIC", "PLACES", DBTypes.H2);

            LinkedHashMap<String, GeometryMetaData> metadata =
                    GeometryTableUtilities.getMetaData(connection, table);
            assertThat(metadata).containsKeys("GEOM", "AREA");
            assertThat(metadata.get("GEOM").getGeometryType()).isEqualTo("POINT");
            assertThat(metadata.get("GEOM").getDimension()).isEqualTo(2);
            assertThat(metadata.get("GEOM").getSRID()).isEqualTo(4326);
            assertThat(metadata.get("AREA").getGeometryType()).isEqualTo("POLYGON");

            Tuple<String, GeometryMetaData> firstColumn =
                    GeometryTableUtilities.getFirstColumnMetaData(connection, table);
            assertThat(firstColumn.first()).isEqualTo("GEOM");
            assertThat(firstColumn.second().getSfs_geometryType()).isEqualTo("POINT");
            assertThat(GeometryTableUtilities.getMetaData(connection, table, "AREA")
                    .getGeometryType()).isEqualTo("POLYGON");
            assertThat(GeometryTableUtilities.getGeometryColumnNames(connection, table))
                    .containsExactly("GEOM", "AREA");
            assertThat(GeometryTableUtilities.getGeometryColumnNamesAndIndexes(connection, table))
                    .hasSize(2)
                    .containsEntry("GEOM", 4)
                    .containsEntry("AREA", 5);
            assertThat(GeometryTableUtilities.getFirstGeometryColumnNameAndIndex(connection, table)
                    .first()).isEqualTo("GEOM");
            assertThat(GeometryTableUtilities.hasGeometryColumn(connection, table)).isTrue();
            assertThat(GeometryTableUtilities.hasGeometryColumn(connection, "PUBLIC.PLACES")).isTrue();
            assertThat(GeometryTableUtilities.getSRID(connection, table, "GEOM")).isEqualTo(4326);
            assertThat(GeometryTableUtilities.getAuthorityAndSRID(connection, table, "GEOM"))
                    .containsExactly("EPSG", "4326");

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT ID, GEOM, AREA FROM PUBLIC.PLACES")) {
                assertThat(GeometryTableUtilities.hasGeometryColumn(resultSet)).isTrue();
                assertThat(GeometryTableUtilities.getFirstGeometryColumnNameAndIndex(
                        resultSet.getMetaData()).first()).isEqualTo("GEOM");
                assertThat(GeometryTableUtilities.getMetaData(resultSet)).containsKeys("GEOM", "AREA");
            }

            Geometry envelope = GeometryTableUtilities.getEnvelope(connection, table);
            assertThat(envelope.getEnvelopeInternal().getMinX()).isEqualTo(-1.0);
            assertThat(envelope.getEnvelopeInternal().getMaxX()).isEqualTo(5.0);
            assertThat(envelope.getEnvelopeInternal().getMinY()).isEqualTo(2.0);
            assertThat(envelope.getEnvelopeInternal().getMaxY()).isEqualTo(6.0);

            Geometry filteredEnvelope = GeometryTableUtilities.getEnvelope(
                    connection,
                    "SELECT GEOM FROM PUBLIC.PLACES WHERE AMOUNT > 10",
                    new String[]{"GEOM"});
            assertThat(filteredEnvelope.getEnvelopeInternal().getMinX()).isEqualTo(-1.0);
            assertThat(filteredEnvelope.getEnvelopeInternal().getMaxX()).isEqualTo(1.0);
            assertThat(filteredEnvelope.getSRID()).isEqualTo(4326);

            GeometryMetaData parsedMetadata =
                    GeometryMetaData.getMetaData("GEOMETRY(POINTZ, 3857)");
            assertThat(parsedMetadata.getGeometryType()).isEqualTo("POINTZ");
            assertThat(parsedMetadata.getDimension()).isEqualTo(3);
            assertThat(parsedMetadata.hasZ()).isTrue();
            assertThat(parsedMetadata.getSRID()).isEqualTo(3857);
        }
    }

    @Test
    void readsGeometryThroughWrappedSpatialResultSet() throws Exception {
        try (Connection connection = openSpatialDatabase("spatial_result_set")) {
            createSpatialTable(connection);
            try (Connection wrappedConnection = JDBCUtilities.wrapConnection(connection);
                    Statement statement = wrappedConnection.createStatement();
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT NAME, GEOM FROM PUBLIC.PLACES WHERE NAME = 'north'")) {
                SpatialResultSet spatialResultSet = resultSet.unwrap(SpatialResultSetImpl.class);

                assertThat(spatialResultSet.next()).isTrue();
                assertThat(spatialResultSet.getString("NAME")).isEqualTo("north");
                assertThat(spatialResultSet.getGeometry("GEOM").getGeometryType())
                        .isEqualTo("Point");
                assertThat(spatialResultSet.getGeometry(2).getSRID()).isEqualTo(4326);
            }
        }
    }

    @Test
    void convertsSpatialRowsToGeoJsonFeatures() throws Exception {
        try (Connection connection = openSpatialDatabase("geometry_features")) {
            createSpatialTable(connection);

            List<?> features = GeometryFeatureUtils.toList(
                    connection,
                    "SELECT NAME, AMOUNT, GEOM FROM PUBLIC.PLACES ORDER BY ID",
                    3);

            assertThat(features).hasSize(3);
            Map<?, ?> firstFeature = (Map<?, ?>) features.get(0);
            assertThat(firstFeature.get("type")).isEqualTo("Feature");
            Map<?, ?> geometry = (Map<?, ?>) firstFeature.get("geometry");
            assertThat(geometry.get("type")).isEqualTo("Point");
            assertThat(geometry.get("coordinates")).isEqualTo(List.of(1.0, 2.0));
            Map<?, ?> properties = (Map<?, ?>) firstFeature.get("properties");
            assertThat(properties.get("NAME")).isEqualTo("north");
            assertThat((BigDecimal) properties.get("AMOUNT"))
                    .isEqualByComparingTo("12.50");
        }
    }

    @Test
    void createsAndRemovesRegularAndSpatialIndexes() throws Exception {
        try (Connection connection = openSpatialDatabase("index_utilities")) {
            createSpatialTable(connection);
            TableLocation table = new TableLocation("PUBLIC", "PLACES", DBTypes.H2);

            assertThat(JDBCUtilities.createIndex(connection, table, "NAME")).isTrue();
            assertThat(JDBCUtilities.isIndexed(connection, table, "NAME")).isTrue();
            assertThat(JDBCUtilities.getIndexNames(connection, table, "NAME"))
                    .isNotEmpty();
            assertThat(JDBCUtilities.getIndexNames(connection, table).values())
                    .contains("NAME");
            JDBCUtilities.dropIndex(connection, table, "NAME");
            assertThat(JDBCUtilities.isIndexed(connection, table, "NAME")).isFalse();

            assertThat(JDBCUtilities.createSpatialIndex(connection, table, "GEOM")).isTrue();
            assertThat(JDBCUtilities.isSpatialIndexed(connection, table, "GEOM")).isTrue();
            assertThat(JDBCUtilities.getIndexNames(connection, table, "GEOM"))
                    .isNotEmpty();
        }
    }

    private static void createSpatialTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE PUBLIC.PLACES (
                        ID INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        NAME VARCHAR(32) NOT NULL,
                        AMOUNT DECIMAL(10, 2),
                        GEOM GEOMETRY(POINT, 4326) NOT NULL,
                        AREA GEOMETRY(POLYGON, 4326) NOT NULL
                    )
                    """);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO PUBLIC.PLACES(NAME, AMOUNT, GEOM, AREA)
                    VALUES (?, ?, ST_GeomFromText(?, 4326), ST_GeomFromText(?, 4326))
                    """)) {
                insert.setString(1, "north");
                insert.setBigDecimal(2, new BigDecimal("12.50"));
                insert.setString(3, "POINT (1 2)");
                insert.setString(4, "POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))");
                insert.executeUpdate();

                insert.setString(1, "south");
                insert.setBigDecimal(2, new BigDecimal("7.25"));
                insert.setString(3, "POINT (5 6)");
                insert.setString(4, "POLYGON ((3 3, 7 3, 7 5, 3 5, 3 3))");
                insert.executeUpdate();

                insert.setString(1, "east");
                insert.setBigDecimal(2, new BigDecimal("15.00"));
                insert.setString(3, "POINT (-1 4)");
                insert.setString(4, "POLYGON ((-2 1, 0 1, 0 3, -2 3, -2 1))");
                insert.executeUpdate();
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
