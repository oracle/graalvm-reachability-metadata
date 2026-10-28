/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_geolatte.geolatte_geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.geolatte.geom.builder.DSL.c;
import static org.geolatte.geom.builder.DSL.geometrycollection;
import static org.geolatte.geom.builder.DSL.linestring;
import static org.geolatte.geom.builder.DSL.multilinestring;
import static org.geolatte.geom.builder.DSL.multipoint;
import static org.geolatte.geom.builder.DSL.multipolygon;
import static org.geolatte.geom.builder.DSL.point;
import static org.geolatte.geom.builder.DSL.polygon;
import static org.geolatte.geom.builder.DSL.ring;
import static org.geolatte.geom.crs.CoordinateReferenceSystems.PROJECTED_2D_METER;

import org.geolatte.geom.C2D;
import org.geolatte.geom.GeometryCollection;
import org.geolatte.geom.LineString;
import org.geolatte.geom.LinearRing;
import org.geolatte.geom.MultiLineString;
import org.geolatte.geom.MultiPoint;
import org.geolatte.geom.MultiPolygon;
import org.geolatte.geom.Point;
import org.geolatte.geom.Polygon;
import org.junit.jupiter.api.Test;

public class DSLTest {
    @Test
    void combinesPointsAndLinesInInputOrder() {
        Point<C2D> firstPoint = point(PROJECTED_2D_METER, c(2, 3));
        Point<C2D> secondPoint = point(PROJECTED_2D_METER, c(5, 7));
        LineString<C2D> firstLine = linestring(PROJECTED_2D_METER, c(0, 0), c(1, 2));
        LineString<C2D> secondLine = linestring(PROJECTED_2D_METER, c(3, 4), c(5, 6));

        MultiPoint<C2D> points = multipoint(firstPoint, secondPoint);
        MultiLineString<C2D> lines = multilinestring(firstLine, secondLine);
        GeometryCollection<C2D> collection = geometrycollection(points, lines, firstPoint);

        assertThat(points.getNumGeometries()).isEqualTo(2);
        assertThat(points.getGeometryN(0)).isEqualTo(firstPoint);
        assertThat(points.getGeometryN(1)).isEqualTo(secondPoint);
        assertThat(lines.getNumGeometries()).isEqualTo(2);
        assertThat(lines.getGeometryN(0)).isEqualTo(firstLine);
        assertThat(lines.getGeometryN(1)).isEqualTo(secondLine);
        assertThat(collection.getNumGeometries()).isEqualTo(3);
        assertThat(collection.getGeometryN(0)).isEqualTo(points);
        assertThat(collection.getGeometryN(1)).isEqualTo(lines);
        assertThat(collection.getGeometryN(2)).isEqualTo(firstPoint);
        assertThat(collection.getCoordinateReferenceSystem()).isEqualTo(PROJECTED_2D_METER);
    }

    @Test
    void combinesExteriorAndInteriorRingsAndPolygons() {
        LinearRing<C2D> exterior = ring(PROJECTED_2D_METER, c(0, 0), c(10, 0), c(10, 10), c(0, 10), c(0, 0));
        LinearRing<C2D> interior = ring(PROJECTED_2D_METER, c(2, 2), c(2, 4), c(4, 4), c(4, 2), c(2, 2));
        LinearRing<C2D> otherExterior = ring(PROJECTED_2D_METER, c(20, 0), c(25, 0), c(25, 5), c(20, 0));

        Polygon<C2D> withHole = polygon(exterior, interior);
        Polygon<C2D> other = polygon(otherExterior);
        MultiPolygon<C2D> polygons = multipolygon(withHole, other);

        assertThat(withHole.getExteriorRing()).isEqualTo(exterior);
        assertThat(withHole.getNumInteriorRing()).isEqualTo(1);
        assertThat(withHole.getInteriorRingN(0)).isEqualTo(interior);
        assertThat(other.getNumInteriorRing()).isZero();
        assertThat(polygons.getNumGeometries()).isEqualTo(2);
        assertThat(polygons.getGeometryN(0)).isEqualTo(withHole);
        assertThat(polygons.getGeometryN(1)).isEqualTo(other);
    }
}
