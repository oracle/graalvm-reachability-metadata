/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wololo.flatgeobuf;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.LittleEndianDataInputStream;
import com.google.flatbuffers.FlatBufferBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.wololo.flatgeobuf.ColumnMeta;
import org.wololo.flatgeobuf.Constants;
import org.wololo.flatgeobuf.GeometryConversions;
import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.NodeItem;
import org.wololo.flatgeobuf.PackedRTree;
import org.wololo.flatgeobuf.generated.Column;
import org.wololo.flatgeobuf.generated.ColumnType;
import org.wololo.flatgeobuf.generated.Feature;
import org.wololo.flatgeobuf.generated.GeometryType;
import org.wololo.flatgeobuf.generated.Header;

public class FlatgeobufTest {
    @Test
    void roundTripsGeometryFamilies() throws IOException {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry point = geometryFactory.createPoint(new CoordinateXYZM(1.25, 2.5, 3.75, 4.5));
        Geometry lineString = geometryFactory.createLineString(
                new Coordinate[] {
                    new Coordinate(0, 0), new Coordinate(2, 1), new Coordinate(4, 0)
                });
        LinearRing shell = geometryFactory.createLinearRing(
                new Coordinate[] {
                    new Coordinate(0, 0),
                    new Coordinate(10, 0),
                    new Coordinate(10, 10),
                    new Coordinate(0, 10),
                    new Coordinate(0, 0)
                });
        LinearRing hole = geometryFactory.createLinearRing(
                new Coordinate[] {
                    new Coordinate(2, 2),
                    new Coordinate(2, 4),
                    new Coordinate(4, 4),
                    new Coordinate(4, 2),
                    new Coordinate(2, 2)
                });
        Geometry polygon = geometryFactory.createPolygon(shell, new LinearRing[] {hole});
        Geometry multiPoint = geometryFactory.createMultiPointFromCoords(
                new Coordinate[] {new Coordinate(-1, -2), new Coordinate(3, 4)});
        Geometry multiLineString = geometryFactory.createMultiLineString(
                new LineString[] {
                    geometryFactory.createLineString(
                            new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 1)}),
                    geometryFactory.createLineString(
                            new Coordinate[] {new Coordinate(2, 2), new Coordinate(3, 3)})
                });
        Geometry secondPolygon = geometryFactory.createPolygon(
                geometryFactory.createLinearRing(
                        new Coordinate[] {
                            new Coordinate(20, 20),
                            new Coordinate(24, 20),
                            new Coordinate(24, 24),
                            new Coordinate(20, 24),
                            new Coordinate(20, 20)
                        }),
                null);
        Geometry multiPolygon = geometryFactory.createMultiPolygon(
                new Polygon[] {
                    (Polygon) polygon,
                    (Polygon) secondPolygon
                });

        assertGeometryRoundTrip(point, (byte) 0, GeometryType.Point);
        assertGeometryRoundTrip(
                lineString, (byte) GeometryType.LineString, GeometryType.LineString);
        assertGeometryRoundTrip(polygon, (byte) GeometryType.Polygon, GeometryType.Polygon);
        assertGeometryRoundTrip(
                multiPoint, (byte) GeometryType.MultiPoint, GeometryType.MultiPoint);
        assertGeometryRoundTrip(
                multiLineString, (byte) GeometryType.MultiLineString, GeometryType.MultiLineString);
        assertGeometryRoundTrip(
                multiPolygon, (byte) GeometryType.MultiPolygon, GeometryType.MultiPolygon);
    }

    @Test
    void infersGeometryTypeWhenSerializing() throws IOException {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry lineString = geometryFactory.createLineString(
                new Coordinate[] {new Coordinate(0, 0), new Coordinate(2, 1)});
        Geometry polygon = geometryFactory.createPolygon(
                geometryFactory.createLinearRing(
                        new Coordinate[] {
                            new Coordinate(0, 0),
                            new Coordinate(4, 0),
                            new Coordinate(4, 4),
                            new Coordinate(0, 4),
                            new Coordinate(0, 0)
                        }),
                null);
        Geometry multiPoint = geometryFactory.createMultiPointFromCoords(
                new Coordinate[] {new Coordinate(-1, -2), new Coordinate(3, 4)});
        Geometry multiLineString = geometryFactory.createMultiLineString(
                new LineString[] {
                    geometryFactory.createLineString(
                            new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 1)}),
                    geometryFactory.createLineString(
                            new Coordinate[] {new Coordinate(2, 2), new Coordinate(3, 3)})
                });
        Geometry multiPolygon = geometryFactory.createMultiPolygon(
                new Polygon[] {(Polygon) polygon});

        assertGeometryRoundTrip(lineString, (byte) 0, GeometryType.LineString);
        assertGeometryRoundTrip(polygon, (byte) 0, GeometryType.Polygon);
        assertGeometryRoundTrip(multiPoint, (byte) 0, GeometryType.MultiPoint);
        assertGeometryRoundTrip(
                multiLineString, (byte) 0, GeometryType.MultiLineString);
        assertGeometryRoundTrip(multiPolygon, (byte) 0, GeometryType.MultiPolygon);
    }

    @Test
    void writesAndReadsHeaderMetadataThroughBothInputForms() throws IOException {
        ColumnMeta nameColumn = new ColumnMeta();
        nameColumn.name = "name";
        nameColumn.type = (byte) ColumnType.String;
        nameColumn.width = 80;
        nameColumn.nullable = false;
        nameColumn.unique = true;

        ColumnMeta populationColumn = new ColumnMeta();
        populationColumn.name = "population";
        populationColumn.type = (byte) ColumnType.Int;
        populationColumn.precision = 10;
        populationColumn.scale = 0;
        populationColumn.nullable = true;

        HeaderMeta metadata = new HeaderMeta();
        metadata.name = "cities";
        metadata.geometryType = (byte) GeometryType.Point;
        metadata.srid = 4326;
        metadata.envelope = new Envelope(-10, 20, -5, 30);
        metadata.featuresCount = 2;
        metadata.hasZ = true;
        metadata.hasM = true;
        metadata.hasT = true;
        metadata.hasTM = true;
        metadata.indexNodeSize = 2;
        metadata.columns = List.of(nameColumn, populationColumn);

        byte[] encoded = writeHeader(metadata);
        assertThat(Constants.isFlatgeobuf(ByteBuffer.wrap(encoded))).isTrue();

        HeaderMeta fromBuffer = HeaderMeta.read(
                ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN));
        HeaderMeta fromStream = HeaderMeta.read(new ByteArrayInputStream(encoded));
        assertHeaderMetadata(fromBuffer);
        assertHeaderMetadata(fromStream);

        ByteBuffer headerBuffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        headerBuffer.position(Constants.MAGIC_BYTES.length + Integer.BYTES);
        Header header = Header.getRootAsHeader(headerBuffer);
        assertThat(header.name()).isEqualTo("cities");
        assertThat(header.geometryType()).isEqualTo(GeometryType.Point);
        assertThat(header.featuresCount()).isEqualTo(2);
        assertThat(header.crs().code()).isEqualTo(4326);
        assertThat(header.columnsLength()).isEqualTo(2);
        assertThat(header.columns(0).name()).isEqualTo("name");
        assertThat(header.columns(0).unique()).isTrue();
        assertThat(header.columns(1).name()).isEqualTo("population");
        assertThat(header.envelopeLength()).isEqualTo(4);
        assertThat(header.envelope(0)).isEqualTo(-10.0);
        assertThat(header.envelope(3)).isEqualTo(30.0);
    }

    @Test
    void writesAndReadsFeatureWithGeometryPropertiesAndColumns() throws IOException {
        GeometryFactory geometryFactory = new GeometryFactory();
        Geometry geometry = geometryFactory.createLineString(
                new Coordinate[] {new Coordinate(1, 2), new Coordinate(3, 4)});
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);
        int geometryOffset = GeometryConversions.serialize(
                builder, geometry, (byte) GeometryType.LineString);
        int propertiesOffset = Feature.createPropertiesVector(builder, new byte[] {7, 2, -1});
        int columnNameOffset = builder.createString("population");
        int columnOffset = Column.createColumn(
                builder,
                columnNameOffset,
                ColumnType.Int,
                0,
                0,
                32,
                0,
                0,
                true,
                false,
                false,
                0);
        int columnsOffset = Feature.createColumnsVector(builder, new int[] {columnOffset});
        int featureOffset = Feature.createFeature(
                builder, geometryOffset, propertiesOffset, columnsOffset);
        Feature.finishFeatureBuffer(builder, featureOffset);

        Feature feature = Feature.getRootAsFeature(builder.dataBuffer());
        assertThat(feature.geometry().type()).isZero();
        assertThat(feature.geometry().xyLength()).isEqualTo(4);
        assertThat(feature.geometry().xy(0)).isEqualTo(1.0);
        assertThat(feature.geometry().xy(3)).isEqualTo(4.0);
        assertThat(feature.propertiesLength()).isEqualTo(3);
        assertThat(feature.properties(0)).isEqualTo(7);
        assertThat(feature.properties(1)).isEqualTo(2);
        assertThat(feature.properties(2)).isEqualTo(255);
        assertThat(feature.columnsLength()).isEqualTo(1);
        assertThat(feature.columns(0).name()).isEqualTo("population");
        assertThat(feature.columns(0).type()).isEqualTo(ColumnType.Int);
        assertThat(feature.columns(0).width()).isEqualTo(32);
        assertThat(feature.columns(0).nullable()).isTrue();
    }

    @Test
    void resolvesColumnTypesToJavaBindings() {
        assertThat(binding(ColumnType.Byte)).isEqualTo(Byte.class);
        assertThat(binding(ColumnType.Bool)).isEqualTo(Boolean.class);
        assertThat(binding(ColumnType.Short)).isEqualTo(Short.class);
        assertThat(binding(ColumnType.Int)).isEqualTo(Integer.class);
        assertThat(binding(ColumnType.Long)).isEqualTo(Long.class);
        assertThat(binding(ColumnType.Double)).isEqualTo(Double.class);
        assertThat(binding(ColumnType.String)).isEqualTo(String.class);
        assertThat(binding(ColumnType.DateTime)).isEqualTo(String.class);
    }

    @Test
    void buildsAndQueriesPackedRTreeFromBufferAndStream() throws IOException {
        List<PackedRTree.Item> items = new ArrayList<>();
        items.add(item(0, 0, 1, 1, 100));
        items.add(item(10, 10, 11, 11, 200));
        items.add(item(20, 20, 21, 21, 300));
        items.add(item(30, 30, 31, 31, 400));

        NodeItem extent = PackedRTree.calcExtent(items);
        assertThat(extent.minX).isEqualTo(0.0);
        assertThat(extent.minY).isEqualTo(0.0);
        assertThat(extent.maxX).isEqualTo(31.0);
        assertThat(extent.maxY).isEqualTo(31.0);
        assertThat(extent.toEnvelope()).isEqualTo(new Envelope(0, 31, 0, 31));
        assertThat(items.get(0).nodeItem.intersects(new NodeItem(1, 1, 2, 2))).isTrue();
        assertThat(items.get(0).nodeItem.intersects(new NodeItem(2, 2, 3, 3))).isFalse();

        List<PackedRTree.Item> sortableItems = new ArrayList<>(items);
        assertThat(PackedRTree.hilbertSort(sortableItems, extent)).isSameAs(sortableItems);

        PackedRTree tree = new PackedRTree(items, (short) 2);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        tree.write(output);
        byte[] index = output.toByteArray();
        assertThat(index).hasSize((int) PackedRTree.calcSize(items.size(), 2));

        Envelope query = new Envelope(9, 12, 9, 12);
        ByteBuffer indexBuffer = ByteBuffer.wrap(index).order(ByteOrder.LITTLE_ENDIAN);
        List<PackedRTree.SearchHit> bufferHits = PackedRTree.search(
                indexBuffer, 0, items.size(), 2, query);
        assertThat(bufferHits).extracting(hit -> hit.offset).containsExactly(200L);

        PackedRTree.SearchResult streamResult = PackedRTree.search(
                new ByteArrayInputStream(index), 0, items.size(), 2, query);
        assertThat(streamResult.hits).extracting(hit -> hit.offset).containsExactly(200L);
        assertThat(streamResult.pos).isPositive();

        HeaderMeta header = new HeaderMeta();
        header.featuresCount = items.size();
        header.indexNodeSize = 2;
        LittleEndianDataInputStream input = new LittleEndianDataInputStream(
                new ByteArrayInputStream(index));
        long[] featureOffsets = PackedRTree.readFeatureOffsets(
                input, new long[] {0, 1, 2, 3}, header);
        assertThat(featureOffsets).containsExactly(100L, 200L, 300L, 400L);

        assertThat(PackedRTree.search(
                ByteBuffer.wrap(index).order(ByteOrder.LITTLE_ENDIAN),
                0,
                items.size(),
                2,
                new Envelope(50, 60, 50, 60))).isEmpty();
    }

    private static void assertGeometryRoundTrip(
            Geometry original, byte geometryType, int expectedType) throws IOException {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);
        int geometryOffset = GeometryConversions.serialize(builder, original, geometryType);
        builder.finish(geometryOffset);

        org.wololo.flatgeobuf.generated.Geometry encoded =
                org.wololo.flatgeobuf.generated.Geometry.getRootAsGeometry(builder.dataBuffer());
        Geometry decoded = GeometryConversions.deserialize(encoded, expectedType);

        int storedType = geometryType == 0 ? expectedType : 0;
        assertThat(encoded.type()).isEqualTo(storedType);
        assertThat(decoded.getGeometryType()).isEqualTo(original.getGeometryType());
        assertThat(decoded.getCoordinates()).hasSize(original.getCoordinates().length);
        for (int i = 0; i < original.getCoordinates().length; i++) {
            Coordinate expected = original.getCoordinates()[i];
            Coordinate actual = decoded.getCoordinates()[i];
            assertThat(actual.getX()).isEqualTo(expected.getX());
            assertThat(actual.getY()).isEqualTo(expected.getY());
            assertOrdinateEqual(actual.getZ(), expected.getZ());
            assertOrdinateEqual(actual.getM(), expected.getM());
        }
    }

    private static void assertOrdinateEqual(double actual, double expected) {
        if (Double.isNaN(expected)) {
            assertThat(actual).isNaN();
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static byte[] writeHeader(HeaderMeta metadata) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        HeaderMeta.write(metadata, payload, new FlatBufferBuilder(1024));

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(Constants.MAGIC_BYTES);
        result.write(payload.toByteArray());
        return result.toByteArray();
    }

    private static void assertHeaderMetadata(HeaderMeta metadata) {
        assertThat(metadata.geometryType).isEqualTo((byte) GeometryType.Point);
        assertThat(metadata.srid).isEqualTo(4326);
        assertThat(metadata.envelope).isEqualTo(new Envelope(-10, 20, -5, 30));
        assertThat(metadata.featuresCount).isEqualTo(2);
        assertThat(metadata.hasZ).isTrue();
        assertThat(metadata.hasM).isTrue();
        assertThat(metadata.hasT).isTrue();
        assertThat(metadata.hasTM).isTrue();
        assertThat(metadata.indexNodeSize).isEqualTo(2);
        assertThat(metadata.columns).hasSize(2);
        assertThat(metadata.columns.get(0).name).isEqualTo("name");
        assertThat(metadata.columns.get(0).type).isEqualTo((byte) ColumnType.String);
        assertThat(metadata.columns.get(1).name).isEqualTo("population");
        assertThat(metadata.columns.get(1).type).isEqualTo((byte) ColumnType.Int);
    }

    private static Class<?> binding(int type) {
        ColumnMeta column = new ColumnMeta();
        column.type = (byte) type;
        return column.getBinding();
    }

    private static PackedRTree.Item item(
            double minX, double minY, double maxX, double maxY, long offset) {
        PackedRTree.Item item = new PackedRTree.Item();
        item.nodeItem = new NodeItem(minX, minY, maxX, maxY, offset);
        return item;
    }
}
