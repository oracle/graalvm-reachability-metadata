/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_locationtech_jts_jts_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.algorithm.Centroid;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle;
import org.locationtech.jts.coverage.CoverageUnion;
import org.locationtech.jts.coverage.CoverageValidator;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

public class JtsCoreTest {
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    void parsesAndRoundTripsGeometryThroughTextAndBinaryFormats() throws Exception {
        Geometry original = new WKTReader(geometryFactory).read(
                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), "
                        + "(2 2, 2 4, 4 4, 4 2, 2 2))");

        WKBWriter writer = new WKBWriter();
        String encoded = WKBWriter.toHex(writer.write(original));
        Geometry decoded = new WKBReader(geometryFactory).read(WKBReader.hexToBytes(encoded));

        assertThat(original.getGeometryType()).isEqualTo("Polygon");
        assertThat(original.getArea()).isEqualTo(96.0);
        assertThat(decoded.equalsExact(original)).isTrue();
        assertThat(original.toText()).contains("POLYGON");
    }

    @Test
    void performsOverlayPredicatesAndPreparedGeometryQueries() throws Exception {
        WKTReader reader = new WKTReader(geometryFactory);
        Geometry left = reader.read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        Geometry right = reader.read("POLYGON ((5 0, 15 0, 15 10, 5 10, 5 0))");
        Geometry point = reader.read("POINT (3 3)");

        PreparedGeometry prepared = PreparedGeometryFactory.prepare(left);
        Geometry intersection = left.intersection(right);
        Geometry union = left.union(right);
        Geometry buffered = point.buffer(2.0);

        assertThat(intersection.getArea()).isEqualTo(50.0);
        assertThat(union.getArea()).isEqualTo(150.0);
        assertThat(left.difference(right).getArea()).isEqualTo(50.0);
        assertThat(prepared.contains(point)).isTrue();
        assertThat(buffered.contains(point)).isTrue();
        assertThat(buffered.getEnvelopeInternal()).isEqualTo(new Envelope(1, 5, 1, 5));
    }

    @Test
    void indexesAndQueriesGeometriesWithSpatialTree() {
        STRtree index = new STRtree();
        index.insert(new Envelope(0, 2, 0, 2), "near");
        index.insert(new Envelope(10, 12, 10, 12), "far");

        List<?> matches = index.query(new Envelope(-1, 3, -1, 3));

        assertThat(index.size()).isEqualTo(2);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0)).isEqualTo("near");
        assertThat(index.remove(new Envelope(10, 12, 10, 12), "far")).isTrue();
        assertThat(index.size()).isEqualTo(1);
    }

    @Test
    void transformsAndExtractsPositionsFromLinearGeometry() throws Exception {
        Geometry line = new WKTReader(geometryFactory).read("LINESTRING (0 0, 10 0, 10 10)");
        Coordinate transformed = AffineTransformation.translationInstance(3, -2)
                .transform(new Coordinate(1, 2), new Coordinate());
        LengthIndexedLine indexedLine = new LengthIndexedLine(line);
        LineString extracted = (LineString) indexedLine.extractLine(5, 15);

        assertThat(transformed).isEqualTo(new Coordinate(4, 0));
        assertThat(indexedLine.extractPoint(15)).isEqualTo(new Coordinate(10, 5));
        assertThat(extracted.getLength()).isEqualTo(10.0);
        assertThat(extracted.getStartPoint().getCoordinate()).isEqualTo(new Coordinate(5, 0));
        assertThat(extracted.getEndPoint().getCoordinate()).isEqualTo(new Coordinate(10, 5));
    }

    @Test
    void computesHullsCentroidsCirclesAndDensifiedCoordinates() throws Exception {
        Geometry sites = new WKTReader(geometryFactory).read(
                "MULTIPOINT ((0 0), (10 0), (10 10), (0 10), (5 5))");
        Geometry square = new WKTReader(geometryFactory).read(
                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        Geometry line = new WKTReader(geometryFactory).read("LINESTRING (0 0, 10 0)");
        MinimumBoundingCircle circle = new MinimumBoundingCircle(square);

        Geometry hull = new ConvexHull(sites).getConvexHull();
        Coordinate centroid = Centroid.getCentroid(square);
        Geometry densified = Densifier.densify(line, 2.0);

        assertThat(hull.getArea()).isEqualTo(100.0);
        assertThat(centroid).isEqualTo(new Coordinate(5, 5));
        assertThat(circle.getCentre()).isEqualTo(new Coordinate(5, 5));
        assertThat(circle.getRadius()).isEqualTo(Math.sqrt(50));
        assertThat(densified.getNumPoints()).isGreaterThan(line.getNumPoints());
    }

    @Test
    void buildsDelaunayAndVoronoiResultsFromPublicBuilders() throws Exception {
        Geometry sites = new WKTReader(geometryFactory).read(
                "MULTIPOINT ((0 0), (10 0), (10 10), (0 10), (5 5))");
        Envelope clip = new Envelope(-1, 11, -1, 11);

        DelaunayTriangulationBuilder delaunay = new DelaunayTriangulationBuilder();
        delaunay.setSites(sites);
        VoronoiDiagramBuilder voronoi = new VoronoiDiagramBuilder();
        voronoi.setSites(sites);
        voronoi.setClipEnvelope(clip);

        Geometry triangles = delaunay.getTriangles(geometryFactory);
        Geometry edges = delaunay.getEdges(geometryFactory);
        Geometry diagram = voronoi.getDiagram(geometryFactory);

        assertThat(triangles.isEmpty()).isFalse();
        assertThat(edges.isEmpty()).isFalse();
        assertThat(diagram.isEmpty()).isFalse();
        assertThat(diagram.getEnvelopeInternal()).isEqualTo(clip);
    }

    @Test
    void validatesAndUnionsAdjacentPolygonCoverage() throws Exception {
        WKTReader reader = new WKTReader(geometryFactory);
        Geometry[] coverage = {
            reader.read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"),
            reader.read("POLYGON ((10 0, 20 0, 20 10, 10 10, 10 0))")
        };

        Geometry union = CoverageUnion.union(coverage);

        assertThat(CoverageValidator.isValid(coverage)).isTrue();
        assertThat(union.getGeometryType()).isEqualTo("Polygon");
        assertThat(union.getArea()).isEqualTo(200.0);
        assertThat(union.getEnvelopeInternal()).isEqualTo(new Envelope(0, 20, 0, 10));
    }

    @Test
    void constructsMaximumInscribedCircleInsidePolygon() throws Exception {
        Geometry square = new WKTReader(geometryFactory).read(
                "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        MaximumInscribedCircle circle = new MaximumInscribedCircle(square, 0.01);

        Coordinate center = circle.getCenter().getCoordinate();

        assertThat(center.getX()).isCloseTo(5.0, offset(0.01));
        assertThat(center.getY()).isCloseTo(5.0, offset(0.01));
        assertThat(circle.getRadiusLine().getLength()).isCloseTo(5.0, offset(0.01));
        assertThat(square.covers(circle.getCenter())).isTrue();
    }

    @Test
    void roundsCoordinatesWithFixedPrecisionFactory() throws Exception {
        GeometryFactory fixedFactory = new GeometryFactory(new PrecisionModel(10));
        Geometry rounded = new WKTReader(fixedFactory).read("POINT (1.24 5.67)");

        assertThat(rounded.getCoordinate()).isEqualTo(new Coordinate(1.2, 5.7));
        assertThat(fixedFactory.getPrecisionModel().getScale()).isEqualTo(10.0);
    }

    @Test
    void createsAndQueriesGeometryCollection() {
        Geometry first = geometryFactory.createPoint(new Coordinate(1, 2));
        Geometry second = geometryFactory.createPoint(new Coordinate(4, 6));
        Geometry collection = geometryFactory.createGeometryCollection(new Geometry[] {first, second});

        assertThat(collection.getNumGeometries()).isEqualTo(2);
        assertThat(first.distance(second)).isEqualTo(5.0);
        assertThat(collection.getEnvelopeInternal()).isEqualTo(new Envelope(1, 4, 2, 6));
    }
}
