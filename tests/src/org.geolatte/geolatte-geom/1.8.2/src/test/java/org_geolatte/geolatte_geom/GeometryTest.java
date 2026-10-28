/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_geolatte.geolatte_geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.geolatte.geom.builder.DSL.c;
import static org.geolatte.geom.builder.DSL.g;
import static org.geolatte.geom.builder.DSL.geometrycollection;
import static org.geolatte.geom.builder.DSL.linestring;
import static org.geolatte.geom.builder.DSL.multilinestring;
import static org.geolatte.geom.builder.DSL.multipoint;
import static org.geolatte.geom.builder.DSL.multipolygon;
import static org.geolatte.geom.builder.DSL.point;
import static org.geolatte.geom.builder.DSL.polygon;
import static org.geolatte.geom.builder.DSL.ring;
import static org.geolatte.geom.crs.CoordinateReferenceSystems.PROJECTED_2D_METER;
import static org.geolatte.geom.crs.CoordinateReferenceSystems.PROJECTED_3D_METER;
import static org.geolatte.geom.crs.CoordinateReferenceSystems.WGS84;

import org.geolatte.geom.C2D;
import org.geolatte.geom.C3D;
import org.geolatte.geom.G2D;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.GeometryCollection;
import org.geolatte.geom.LineString;
import org.geolatte.geom.MultiPoint;
import org.geolatte.geom.Point;
import org.geolatte.geom.Polygon;
import org.junit.jupiter.api.Test;

public class GeometryTest {
    @Test
    void assignsGeographicCrsToEveryNestedCollectionComponent() {
        Point<C2D> sourcePoint = point(PROJECTED_2D_METER, c(1, 2));
        LineString<C2D> sourceLine = linestring(PROJECTED_2D_METER, c(0, 0), c(5, 6));
        Polygon<C2D> sourcePolygon = polygon(ring(PROJECTED_2D_METER,
                c(0, 0), c(8, 0), c(8, 8), c(0, 0)));
        GeometryCollection<C2D> source = geometrycollection(
                multipoint(sourcePoint), multilinestring(sourceLine), multipolygon(sourcePolygon));
        Point<G2D> expectedPoint = point(WGS84, g(1, 2));
        LineString<G2D> expectedLine = linestring(WGS84, g(0, 0), g(5, 6));
        Polygon<G2D> expectedPolygon = polygon(ring(WGS84,
                g(0, 0), g(8, 0), g(8, 8), g(0, 0)));
        GeometryCollection<G2D> expected = geometrycollection(
                multipoint(expectedPoint), multilinestring(expectedLine), multipolygon(expectedPolygon));

        Geometry<G2D> converted = Geometry.forceToCrs(source, WGS84);

        assertThat(converted).isEqualTo(expected);
        assertThat(converted.getCoordinateDimension()).isEqualTo(2);
        assertThat(converted.getCoordinateReferenceSystem()).isEqualTo(WGS84);
        assertThat(source.getCoordinateReferenceSystem()).isEqualTo(PROJECTED_2D_METER);
        assertThat(sourcePoint.getPosition()).isEqualTo(c(1, 2));
    }

    @Test
    void expandsMultiPointComponentsWithDefaultMissingHeight() {
        MultiPoint<C2D> source = multipoint(
                point(PROJECTED_2D_METER, c(2, 3)), point(PROJECTED_2D_METER, c(5, 7)));
        Geometry<C3D> converted = Geometry.forceToCrs(source, PROJECTED_3D_METER);

        assertThat(converted).isInstanceOf(MultiPoint.class);
        assertThat(converted.getCoordinateDimension()).isEqualTo(3);
        assertThat(converted.getCoordinateReferenceSystem()).isEqualTo(PROJECTED_3D_METER);
        assertThat(converted.getPositions().size()).isEqualTo(2);
        C3D first = converted.getPositions().getPositionN(0);
        C3D second = converted.getPositions().getPositionN(1);
        assertThat(first.getX()).isEqualTo(2);
        assertThat(first.getY()).isEqualTo(3);
        assertThat(first.getZ()).isNaN();
        assertThat(second.getX()).isEqualTo(5);
        assertThat(second.getY()).isEqualTo(7);
        assertThat(second.getZ()).isNaN();
    }
}
