/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_postgis.postgis_geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.postgis.Geometry;
import org.postgis.GeometryBuilder;
import org.postgis.GeometryCollection;
import org.postgis.GeometryTokenizer;
import org.postgis.LineString;
import org.postgis.LinearRing;
import org.postgis.MultiLineString;
import org.postgis.MultiPoint;
import org.postgis.MultiPolygon;
import org.postgis.Point;
import org.postgis.Polygon;
import org.postgis.binary.BinaryParser;
import org.postgis.binary.BinaryWriter;
import org.postgis.binary.ByteGetter;
import org.postgis.binary.ByteSetter;
import org.postgis.binary.ValueGetter;
import org.postgis.binary.ValueSetter;
import org.postgis.util.VersionFunctions;

public class Postgis_geometryTest {

    @Test
    void pointSupportsCoordinatesMeasuresSridAndDistance() throws Exception {
        Point point = new Point("SRID=4326;POINT(1.5 2.5 3.5 4.5)");

        assertThat(point.getType()).isEqualTo(Geometry.POINT);
        assertThat(point.getTypeString()).isEqualTo("POINT");
        assertThat(point.getDimension()).isEqualTo(3);
        assertThat(point.isMeasured()).isTrue();
        assertThat(point.getSrid()).isEqualTo(4326);
        assertThat(point.getX()).isEqualTo(1.5);
        assertThat(point.getY()).isEqualTo(2.5);
        assertThat(point.getZ()).isEqualTo(3.5);
        assertThat(point.getM()).isEqualTo(4.5);
        assertThat(point.numPoints()).isEqualTo(1);
        assertThat(point.getFirstPoint()).isSameAs(point);
        assertThat(point.getLastPoint()).isSameAs(point);
        assertThat(point.getPoint(0)).isSameAs(point);
        assertThat(point.toString()).isEqualTo("SRID=4326;POINT(1.5 2.5 3.5 4.5)");
        assertThat(point.getValue()).isEqualTo("(1.5 2.5 3.5 4.5)");
        assertThat(point.checkConsistency()).isTrue();
        assertThat(point.distance(new Point(2.5, 4.5, 5.5))).isEqualTo(3.0);

        point.setX(10);
        point.setY(20);
        point.setZ(30);
        point.setM(40.25);
        point.setSrid(-1);

        assertThat(point.toString()).isEqualTo("SRID=-1;POINT(10 20 30 40.25)");
        assertThat(point.checkConsistency()).isTrue();
        assertThatThrownBy(() -> point.getPoint(1)).isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void pointEqualityHandlesDimensionsMeasuresAndNanValues() throws Exception {
        Point first = new Point("POINT(NaN 2)");
        Point second = new Point("POINT(NaN 2)");
        Point measured = new Point("POINTM(1 2 3)");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.equals((Object) null)).isFalse();
        assertThat(first.equals("POINT(NaN 2)")).isFalse();
        assertThat(measured.getDimension()).isEqualTo(2);
        assertThat(measured.isMeasured()).isTrue();
        assertThat(measured.getM()).isEqualTo(3.0);
        assertThat(measured.toString()).isEqualTo("POINTM(1 2 3)");

        Point inconsistent = new Point(1, 2);
        inconsistent.z = 7;
        assertThat(inconsistent.checkConsistency()).isFalse();

        Point oneDimensional = new Point(4, 2);
        Point oneDimensionalOther = new Point(1, 10);
        oneDimensional.dimension = 1;
        oneDimensionalOther.dimension = 1;
        assertThat(oneDimensional.distance(oneDimensionalOther)).isEqualTo(3.0);
        assertThatThrownBy(() -> oneDimensional.distance(new Point(1, 2))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lineStringSupportsParsingLengthReverseAndConcat() throws Exception {
        LineString line = new LineString("LINESTRING(0 0,3 4,6 8)");

        assertThat(line.getType()).isEqualTo(Geometry.LINESTRING);
        assertThat(line.numPoints()).isEqualTo(3);
        assertThat(line.getFirstPoint()).isEqualTo(new Point(0, 0));
        assertThat(line.getLastPoint()).isEqualTo(new Point(6, 8));
        assertThat(line.getPoint(1)).isEqualTo(new Point(3, 4));
        assertThat(line.getPoint(-1)).isNull();
        assertThat(line.getPoints()).containsExactly(new Point(0, 0), new Point(3, 4), new Point(6, 8));
        assertThat(line.length()).isEqualTo(10.0);
        assertThat(line.toString()).isEqualTo("LINESTRING(0 0,3 4,6 8)");

        LineString reversed = line.reverse();
        assertThat(reversed.toString()).isEqualTo("LINESTRING(6 8,3 4,0 0)");
        assertThat(reversed.length()).isEqualTo(10.0);

        LineString connected = line.concat(new LineString("LINESTRING(6 8,9 12)"));
        assertThat(connected.toString()).isEqualTo("LINESTRING(0 0,3 4,6 8,9 12)");
        assertThat(connected.length()).isEqualTo(15.0);

        LineString disconnected = line.concat(new LineString("LINESTRING(7 8,9 12)"));
        assertThat(disconnected.toString()).isEqualTo("LINESTRING(0 0,3 4,6 8,7 8,9 12)");
    }

    @Test
    void polygonsExposeRingsPointsAndRecursiveSrid() throws Exception {
        Polygon polygon = new Polygon("POLYGON((0 0,4 0,4 4,0 0),(1 1,2 1,1 1))");

        assertThat(polygon.getType()).isEqualTo(Geometry.POLYGON);
        assertThat(polygon.numRings()).isEqualTo(2);
        assertThat(polygon.numGeoms()).isEqualTo(2);
        assertThat(polygon.numPoints()).isEqualTo(7);
        assertThat(polygon.getFirstPoint()).isEqualTo(new Point(0, 0));
        assertThat(polygon.getLastPoint()).isEqualTo(new Point(1, 1));
        assertThat(polygon.getRing(0).getPoints()).containsExactly(
                new Point(0, 0), new Point(4, 0), new Point(4, 4), new Point(0, 0));
        assertThat(polygon.getRing(1).toString()).isEqualTo("(1 1,2 1,1 1)");
        assertThat(polygon.getRing(2)).isNull();
        assertThat(polygon.toString()).isEqualTo("POLYGON((0 0,4 0,4 4,0 0),(1 1,2 1,1 1))");

        polygon.setSrid(3857);

        assertThat(polygon.getSrid()).isEqualTo(3857);
        assertThat(polygon.getRing(0).getSrid()).isEqualTo(3857);
        assertThat(polygon.getRing(0).getPoint(0).getSrid()).isEqualTo(3857);
        assertThat(polygon.toString()).startsWith("SRID=3857;POLYGON");
        assertThat(polygon.checkConsistency()).isTrue();
    }

    @Test
    void multiGeometriesExposeTheirTypedComponents() throws Exception {
        MultiPoint multiPoint = new MultiPoint("MULTIPOINT((1 2),(3 4),(5 6))");
        assertThat(multiPoint.getType()).isEqualTo(Geometry.MULTIPOINT);
        assertThat(multiPoint.numPoints()).isEqualTo(3);
        assertThat(multiPoint.getPoints()).containsExactly(new Point(1, 2), new Point(3, 4), new Point(5, 6));
        assertThat(multiPoint.toString()).isEqualTo("MULTIPOINT(1 2,3 4,5 6)");

        MultiLineString multiLine = new MultiLineString("MULTILINESTRING((0 0,3 4),(3 4,6 8,9 12))");
        assertThat(multiLine.getType()).isEqualTo(Geometry.MULTILINESTRING);
        assertThat(multiLine.numLines()).isEqualTo(2);
        assertThat(multiLine.numPoints()).isEqualTo(5);
        assertThat(multiLine.getLine(0).length()).isEqualTo(5.0);
        assertThat(multiLine.getLine(2)).isNull();
        assertThat(multiLine.getLines()).hasSize(2);
        assertThat(multiLine.length()).isEqualTo(15.0);

        MultiPolygon multiPolygon = new MultiPolygon(
                "MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((2 2,3 2,3 3,2 2)))");
        assertThat(multiPolygon.getType()).isEqualTo(Geometry.MULTIPOLYGON);
        assertThat(multiPolygon.numPolygons()).isEqualTo(2);
        assertThat(multiPolygon.getPolygon(0).numRings()).isEqualTo(1);
        assertThat(multiPolygon.getPolygon(1).getFirstPoint()).isEqualTo(new Point(2, 2));
        assertThat(multiPolygon.getPolygon(-1)).isNull();
        assertThat(multiPolygon.getPolygons()).hasSize(2);
        assertThat(multiPolygon.checkConsistency()).isTrue();
    }

    @Test
    void geometryCollectionSupportsMixedGeometryTraversalAndEmptyCollections() throws Exception {
        GeometryCollection collection = new GeometryCollection(
                "GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0,3 4),POLYGON((0 0,1 0,1 1,0 0)))");

        assertThat(collection.getType()).isEqualTo(Geometry.GEOMETRYCOLLECTION);
        assertThat(collection.numGeoms()).isEqualTo(3);
        assertThat(collection.numPoints()).isEqualTo(7);
        assertThat(collection.getPoint(0)).isEqualTo(new Point(1, 2));
        assertThat(collection.getPoint(2)).isEqualTo(new Point(3, 4));
        assertThat(collection.getFirstPoint()).isEqualTo(new Point(1, 2));
        assertThat(collection.getLastPoint()).isEqualTo(new Point(0, 0));
        assertThat(collection.getSubGeometry(1)).isInstanceOf(LineString.class);
        assertThat(collection.getGeometries()).hasSize(3);
        assertThat(collection.toString()).isEqualTo(
                "GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0,3 4),POLYGON((0 0,1 0,1 1,0 0)))");

        Iterator<?> iterator = collection.iterator();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isInstanceOf(Point.class);

        Geometry empty = GeometryBuilder.geomFromString("GEOMETRYCOLLECTION EMPTY");
        assertThat(empty).isInstanceOf(GeometryCollection.class);
        assertThat(((GeometryCollection) empty).isEmpty()).isTrue();
        assertThat(empty.numPoints()).isEqualTo(0);
        assertThat(empty.toString()).isEqualTo("GEOMETRYCOLLECTION EMPTY");
        assertThatThrownBy(empty::getFirstPoint).isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void geometryBuilderParsesWktEwktAndHexEncodedWkb() throws Exception {
        Geometry point = GeometryBuilder.geomFromString("SRID=4326;POINT(1 2)");
        assertThat(point).isInstanceOf(Point.class);
        assertThat(point.getSrid()).isEqualTo(4326);
        assertThat(point.toString()).isEqualTo("SRID=4326;POINT(1 2)");

        Geometry measuredLine = GeometryBuilder.geomFromString("LINESTRINGM(0 0 5,3 4 6)");
        assertThat(measuredLine).isInstanceOf(LineString.class);
        assertThat(measuredLine.getDimension()).isEqualTo(2);
        assertThat(measuredLine.isMeasured()).isTrue();
        assertThat(measuredLine.toString()).isEqualTo("LINESTRINGM(0 0 5,3 4 6)");

        String hex = new BinaryWriter().writeHexed(new Point(9, 10), ValueSetter.XDR.NUMBER);
        Geometry parsedFromHex = GeometryBuilder.geomFromString(hex, new BinaryParser());
        assertThat(parsedFromHex).isEqualTo(new Point(9, 10));

        assertThat(GeometryBuilder.splitSRID("SRID=27700;POINT(0 0)"))
                .containsExactly("SRID=27700", "POINT(0 0)");
        assertThat(Geometry.parseSRID(-7)).isEqualTo(0);
        assertThat(Geometry.parseSRID(4326)).isEqualTo(4326);
        assertThat(Geometry.getTypeString(Geometry.MULTIPOLYGON)).isEqualTo("MULTIPOLYGON");
        assertThatThrownBy(() -> Geometry.getTypeString(99)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeometryBuilder.splitSRID("SRID=4326 POINT(0 0)"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> GeometryBuilder.geomFromString("TRIANGLE((0 0,1 1,0 0))"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> new Point("POINT(a b)")).isInstanceOf(SQLException.class);
    }

    @Test
    void explicitMeasuredParsingTreatsThirdCoordinateAsMeasure() throws Exception {
        Geometry measuredPolygon = GeometryBuilder.geomFromString(
                "POLYGON((0 0 7,4 0 8,0 0 7))", true);
        assertThat(measuredPolygon).isInstanceOf(Polygon.class);
        assertThat(measuredPolygon.getDimension()).isEqualTo(2);
        assertThat(measuredPolygon.isMeasured()).isTrue();
        assertThat(measuredPolygon.getFirstPoint().getM()).isEqualTo(7.0);
        assertThat(measuredPolygon.toString()).isEqualTo("POLYGONM((0 0 7,4 0 8,0 0 7))");
        assertThat(measuredPolygon.checkConsistency()).isTrue();

        GeometryCollection measuredCollection = new GeometryCollection(
                "GEOMETRYCOLLECTION(POINT(1 2 3),LINESTRING(0 0 4,3 4 5))", true);
        assertThat(measuredCollection.getDimension()).isEqualTo(2);
        assertThat(measuredCollection.isMeasured()).isTrue();
        assertThat(measuredCollection.getFirstPoint().getM()).isEqualTo(3.0);
        assertThat(measuredCollection.getSubGeometry(1)).isInstanceOf(LineString.class);
        assertThat(measuredCollection.toString()).isEqualTo(
                "GEOMETRYCOLLECTIONM(POINTM(1 2 3),LINESTRINGM(0 0 4,3 4 5))");
        assertThat(measuredCollection.checkConsistency()).isTrue();
    }

    @Test
    void outerWktCanSuppressMeasuredTypeMarker() throws Exception {
        Geometry measuredPoint = GeometryBuilder.geomFromString("POINTM(1 2 3)");
        StringWriter pointWithMeasureMarker = new StringWriter();
        StringWriter pointWithoutMeasureMarker = new StringWriter();

        measuredPoint.outerWKT(pointWithMeasureMarker.getBuffer(), true);
        measuredPoint.outerWKT(pointWithoutMeasureMarker.getBuffer(), false);

        assertThat(pointWithMeasureMarker).hasToString("POINTM(1 2 3)");
        assertThat(pointWithoutMeasureMarker).hasToString("POINT(1 2 3)");

        Geometry measuredLine = GeometryBuilder.geomFromString("LINESTRINGM(0 0 5,3 4 6)");
        StringWriter lineWithoutMeasureMarker = new StringWriter();

        measuredLine.outerWKT(lineWithoutMeasureMarker.getBuffer(), false);

        assertThat(lineWithoutMeasureMarker).hasToString("LINESTRING(0 0 5,3 4 6)");
    }

    @Test
    void binaryWriterAndParserRoundTripRepresentativeGeometriesInBothEndiannesses() throws Exception {
        Geometry[] geometries = new Geometry[] {
                measuredPoint(),
                new LineString("LINESTRING(0 0,3 4,6 8)"),
                new Polygon("POLYGON((0 0,4 0,4 4,0 0),(1 1,2 1,1 1))"),
                new MultiPoint("MULTIPOINT((1 2),(3 4))"),
                new MultiLineString("MULTILINESTRING((0 0,3 4),(3 4,6 8))"),
                new MultiPolygon("MULTIPOLYGON(((0 0,1 0,1 1,0 0)))"),
                new GeometryCollection(new Geometry[] {new Point(1, 2), new LineString("LINESTRING(0 0,3 4)")})
        };
        BinaryWriter writer = new BinaryWriter();
        BinaryParser parser = new BinaryParser();

        for (Geometry geometry : geometries) {
            geometry.setSrid(4326);
            assertRoundTrip(writer, parser, geometry, ValueSetter.NDR.NUMBER);
            assertRoundTrip(writer, parser, geometry, ValueSetter.XDR.NUMBER);
        }

        assertThat(writer.writeHexed(new Point(1.25, -2.5), ValueSetter.NDR.NUMBER))
                .isEqualTo("0101000000000000000000F43F00000000000004C0");
        assertThat(writer.writeHexed(new Point(1.25, -2.5), ValueSetter.XDR.NUMBER))
                .isEqualTo("00000000013FF4000000000000C004000000000000");
    }

    @Test
    void binaryByteAccessorsReadWriteHexBinaryAndPrimitiveValues() {
        ByteSetter.StringByteSetter hexBytes = new ByteSetter.StringByteSetter(13);
        ValueSetter hexSetter = BinaryWriter.valueSetterForEndian(hexBytes, ValueSetter.NDR.NUMBER);
        hexSetter.setByte((byte) 0x7F);
        hexSetter.setInt(0x01020304);
        hexSetter.setLong(0x0102030405060708L);

        ByteGetter.StringByteGetter hexGetter = new ByteGetter.StringByteGetter(hexBytes.result());
        ValueGetter valueGetter = BinaryParser.valueGetterForEndian(new ByteGetter.StringByteGetter("01"));
        assertThat(valueGetter.endian).isEqualTo(ValueGetter.NDR.NUMBER);
        assertThat(hexGetter.get(0)).isEqualTo(0x7F);
        assertThat(hexGetter.get(1)).isEqualTo(0x04);
        assertThat(hexGetter.get(4)).isEqualTo(0x01);
        assertThat(ByteGetter.StringByteGetter.unhex('f')).isEqualTo((byte) 15);
        assertThatThrownBy(() -> ByteGetter.StringByteGetter.unhex('x')).isInstanceOf(IllegalArgumentException.class);

        ByteSetter.BinaryByteSetter binaryBytes = new ByteSetter.BinaryByteSetter(8);
        ValueSetter binarySetter = BinaryWriter.valueSetterForEndian(binaryBytes, ValueSetter.XDR.NUMBER);
        binarySetter.setDouble(12.5);
        ValueGetter binaryGetter = BinaryParser.valueGetterForEndian(new ByteGetter.BinaryByteGetter(
                new byte[] {ValueGetter.XDR.NUMBER}));
        assertThat(binaryGetter.endian).isEqualTo(ValueGetter.XDR.NUMBER);
        ValueGetter doubleGetter = new ValueGetter.XDR(new ByteGetter.BinaryByteGetter(binaryBytes.result()));
        assertThat(doubleGetter.getDouble()).isEqualTo(12.5);
        assertThat(binaryBytes.toString()).hasSize(8);
        assertThat(binarySetter.toString()).contains("XDR");

        assertThatThrownBy(() -> BinaryParser.valueGetterForEndian(new ByteGetter.BinaryByteGetter(new byte[] {2})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BinaryWriter.valueSetterForEndian(hexBytes, (byte) 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenizerRespectsNestedGeometryDelimiters() {
        List<String> geometries = GeometryTokenizer.tokenize(
                "POINT(1 2),LINESTRING(0 0,3 4),POLYGON((0 0,1 0,1 1,0 0))", ',');

        assertThat(geometries).containsExactly(
                "POINT(1 2)", "LINESTRING(0 0,3 4)", "POLYGON((0 0,1 0,1 1,0 0))");
        assertThat(GeometryTokenizer.tokenize("1 2 3", ' ')).containsExactly("1", "2", "3");
        assertThat(GeometryTokenizer.removeLeadingAndTrailingStrings("((1 2),(3 4))", "(", ")"))
                .isEqualTo("(1 2),(3 4)");
        assertThat(GeometryTokenizer.removeLeadingAndTrailingStrings("POINT(1 2)", "[", "]"))
                .isEqualTo("POINT(1 2)");
    }

    @Test
    void constructorsAndEnumsCoverArrayBasedGeometryCreation() {
        LinearRing ring = new LinearRing(new Point[] {
                new Point(0, 0), new Point(1, 0), new Point(1, 1), new Point(0, 0)
        });
        Polygon polygon = new Polygon(new LinearRing[] {ring});
        MultiLineString emptyMultiLine = new MultiLineString(new LineString[0]);
        MultiPolygon multiPolygon = new MultiPolygon(new Polygon[] {polygon});
        GeometryCollection collection = new GeometryCollection(new Geometry[] {polygon, multiPolygon});

        assertThat(new LineString()).satisfies(line -> {
            assertThat(line.getType()).isEqualTo(Geometry.LINESTRING);
            assertThat(line.numPoints()).isEqualTo(0);
        });
        assertThat(new LineString(new Point[0]).length()).isEqualTo(0.0);
        assertThat(emptyMultiLine.isEmpty()).isTrue();
        assertThat(emptyMultiLine.length()).isEqualTo(0.0);
        assertThat(new Polygon().isEmpty()).isTrue();
        assertThat(new MultiPoint().isEmpty()).isTrue();
        assertThat(new MultiPolygon().isEmpty()).isTrue();
        assertThat(new GeometryCollection().isEmpty()).isTrue();
        assertThat(collection.numGeoms()).isEqualTo(2);
        assertThat(collection.getSubGeometry(0)).isSameAs(polygon);
        assertThat(collection.checkConsistency()).isTrue();
        assertThat(Arrays.asList(VersionFunctions.values())).contains(VersionFunctions.POSTGIS_VERSION);
        assertThat(VersionFunctions.valueOf("POSTGIS_LIB_VERSION")).isEqualTo(VersionFunctions.POSTGIS_LIB_VERSION);
    }

    private static Point measuredPoint() {
        Point point = new Point(1, 2, 3);
        point.setM(4);
        return point;
    }

    private static void assertRoundTrip(BinaryWriter writer, BinaryParser parser, Geometry geometry, byte endian) {
        String hex = writer.writeHexed(geometry, endian);
        byte[] binary = writer.writeBinary(geometry, endian);

        Geometry parsedFromHex = parser.parse(hex);
        Geometry parsedFromBinary = parser.parse(binary);

        assertThat(parsedFromHex).isEqualTo(geometry);
        assertThat(parsedFromBinary).isEqualTo(geometry);
        assertThat(parsedFromHex.toString()).isEqualTo(geometry.toString());
        assertThat(parsedFromBinary.toString()).isEqualTo(geometry.toString());
        assertThat(hex).hasSize(binary.length * 2);
        assertThat(parsedFromHex.checkConsistency()).isTrue();
        assertThat(parsedFromBinary.checkConsistency()).isTrue();
    }
}
