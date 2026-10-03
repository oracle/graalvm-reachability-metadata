/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_geographiclib.GeographicLib_Java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import net.sf.geographiclib.Accumulator;
import net.sf.geographiclib.Constants;
import net.sf.geographiclib.GeoMath;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import net.sf.geographiclib.GeodesicLine;
import net.sf.geographiclib.GeodesicMask;
import net.sf.geographiclib.GeographicErr;
import net.sf.geographiclib.Gnomonic;
import net.sf.geographiclib.GnomonicData;
import net.sf.geographiclib.Pair;
import net.sf.geographiclib.PolygonArea;
import net.sf.geographiclib.PolygonResult;
import org.junit.jupiter.api.Test;

public class GeographicLib_JavaTest {
    private static final double ANGULAR_TOLERANCE = 1.0e-12;
    private static final double DISTANCE_TOLERANCE = 1.0e-6;

    @Test
    void solvesDirectInverseAndArcGeodesicProblems() {
        Geodesic earth = Geodesic.WGS84;
        GeodesicData inverse = earth.Inverse(40.0, -75.0, 41.0, -74.0, GeodesicMask.ALL);

        assertThat(inverse.s12).isPositive();
        assertThat(inverse.a12).isPositive();
        assertThat(inverse.m12).isNotNaN();
        assertThat(inverse.M12).isNotNaN();
        assertThat(inverse.M21).isNotNaN();
        assertThat(inverse.S12).isNotNaN();

        GeodesicData direct = earth.Direct(
                inverse.lat1, inverse.lon1, inverse.azi1, inverse.s12, GeodesicMask.ALL);
        GeodesicData arcDirect = earth.ArcDirect(
                inverse.lat1, inverse.lon1, inverse.azi1, inverse.a12, GeodesicMask.ALL);
        GeodesicData generalArc = earth.Direct(
                inverse.lat1, inverse.lon1, inverse.azi1, true, inverse.a12, GeodesicMask.ALL);

        assertThat(direct.lat2).isCloseTo(inverse.lat2, within(ANGULAR_TOLERANCE));
        assertThat(direct.lon2).isCloseTo(inverse.lon2, within(ANGULAR_TOLERANCE));
        assertThat(direct.s12).isCloseTo(inverse.s12, within(DISTANCE_TOLERANCE));
        assertThat(arcDirect.lat2).isCloseTo(inverse.lat2, within(ANGULAR_TOLERANCE));
        assertThat(arcDirect.lon2).isCloseTo(inverse.lon2, within(ANGULAR_TOLERANCE));
        assertThat(generalArc.lat2).isCloseTo(inverse.lat2, within(ANGULAR_TOLERANCE));
        assertThat(generalArc.lon2).isCloseTo(inverse.lon2, within(ANGULAR_TOLERANCE));
        assertThat(earth.EquatorialRadius()).isEqualTo(Constants.WGS84_a);
        assertThat(earth.Flattening()).isEqualTo(Constants.WGS84_f);
        assertThat(earth.EllipsoidArea()).isPositive();

        Geodesic sphere = new Geodesic(6_371_000.0, 0.0);
        GeodesicData quarterEquator = sphere.Inverse(0.0, 0.0, 0.0, 90.0);
        assertThat(quarterEquator.s12)
                .isCloseTo(Math.PI * sphere.EquatorialRadius() / 2.0, within(DISTANCE_TOLERANCE));
    }

    @Test
    void rejectsEllipsoidsWithInvalidAxes() {
        assertThatThrownBy(() -> new Geodesic(0.0, 0.0))
                .isInstanceOf(GeographicErr.class);
        assertThatThrownBy(() -> new Geodesic(Constants.WGS84_a, 1.0))
                .isInstanceOf(GeographicErr.class);
    }

    @Test
    void followsGeodesicLinesWithCapabilitiesAndDistanceModes() {
        Geodesic earth = Geodesic.WGS84;
        GeodesicData inverse = earth.Inverse(40.0, -75.0, 41.0, -74.0, GeodesicMask.ALL);
        GeodesicLine line = earth.Line(40.0, -75.0, inverse.azi1, GeodesicMask.ALL);

        assertThat(line.Capabilities()).isPositive();
        assertThat(line.Capabilities(GeodesicMask.DISTANCE_IN)).isTrue();
        assertThat(line.Capabilities(GeodesicMask.AREA)).isTrue();
        assertThat(line.Latitude()).isCloseTo(40.0, within(ANGULAR_TOLERANCE));
        assertThat(line.Longitude()).isCloseTo(-75.0, within(ANGULAR_TOLERANCE));
        assertThat(line.Azimuth()).isCloseTo(inverse.azi1, within(ANGULAR_TOLERANCE));
        assertThat(line.EquatorialRadius()).isEqualTo(Constants.WGS84_a);
        assertThat(line.Flattening()).isEqualTo(Constants.WGS84_f);

        Pair azimuthCosines = line.AzimuthCosines();
        assertThat(Math.hypot(azimuthCosines.first, azimuthCosines.second))
                .isCloseTo(1.0, within(ANGULAR_TOLERANCE));
        Pair equatorialAzimuthCosines = line.EquatorialAzimuthCosines();
        assertThat(Math.hypot(equatorialAzimuthCosines.first, equatorialAzimuthCosines.second))
                .isCloseTo(1.0, within(ANGULAR_TOLERANCE));
        assertThat(line.EquatorialAzimuth()).isFinite();
        assertThat(line.EquatorialArc()).isFinite();

        GeodesicData position = line.Position(
                inverse.s12, GeodesicMask.ALL | GeodesicMask.LONG_UNROLL);
        GeodesicData arcPosition = line.ArcPosition(inverse.a12, GeodesicMask.ALL);
        assertThat(position.lat2).isCloseTo(inverse.lat2, within(ANGULAR_TOLERANCE));
        assertThat(position.lon2).isCloseTo(inverse.lon2, within(ANGULAR_TOLERANCE));
        assertThat(arcPosition.lat2).isCloseTo(inverse.lat2, within(ANGULAR_TOLERANCE));
        assertThat(arcPosition.lon2).isCloseTo(inverse.lon2, within(ANGULAR_TOLERANCE));

        line.SetDistance(inverse.s12);
        assertThat(line.Distance()).isCloseTo(inverse.s12, within(DISTANCE_TOLERANCE));
        assertThat(line.Arc()).isCloseTo(inverse.a12, within(ANGULAR_TOLERANCE));
        line.GenSetDistance(true, inverse.a12);
        assertThat(line.Arc()).isCloseTo(inverse.a12, within(ANGULAR_TOLERANCE));

        GeodesicLine directLine = earth.DirectLine(40.0, -75.0, inverse.azi1, inverse.s12);
        GeodesicLine arcDirectLine = earth.ArcDirectLine(40.0, -75.0, inverse.azi1, inverse.a12);
        GeodesicLine inverseLine = earth.InverseLine(40.0, -75.0, 41.0, -74.0);
        GeodesicLine generatedLine = earth.GenDirectLine(
                40.0, -75.0, inverse.azi1, false, inverse.s12, GeodesicMask.ALL);

        assertThat(directLine.Position(inverse.s12).lat2)
                .isCloseTo(inverse.lat2, within(ANGULAR_TOLERANCE));
        assertThat(arcDirectLine.ArcPosition(inverse.a12).lon2)
                .isCloseTo(inverse.lon2, within(ANGULAR_TOLERANCE));
        assertThat(inverseLine.Position(inverse.s12).lat2)
                .isCloseTo(inverse.lat2, within(ANGULAR_TOLERANCE));
        assertThat(generatedLine.Position(inverse.s12).lon2)
                .isCloseTo(inverse.lon2, within(ANGULAR_TOLERANCE));
    }

    @Test
    void reportsGeodesicLineDistanceInDistanceAndArcUnits() {
        GeodesicLine line = Geodesic.WGS84.Line(10.0, 20.0, 35.0, GeodesicMask.ALL);
        double distance = 250_000.0;
        GeodesicData position = line.Position(distance, GeodesicMask.ALL);

        line.SetDistance(distance);

        assertThat(line.GenDistance(false)).isCloseTo(distance, within(DISTANCE_TOLERANCE));
        assertThat(line.GenDistance(true)).isCloseTo(position.a12, within(ANGULAR_TOLERANCE));
    }

    @Test
    void computesPolygonsAndPolylinesFromGeodesicEdges() {
        Geodesic earth = Geodesic.WGS84;
        PolygonArea polygon = new PolygonArea(earth, false);

        Pair emptyPoint = polygon.CurrentPoint();
        assertThat(emptyPoint.first).isNaN();
        assertThat(emptyPoint.second).isNaN();
        assertThat(polygon.EquatorialRadius()).isEqualTo(Constants.WGS84_a);
        assertThat(polygon.Flattening()).isEqualTo(Constants.WGS84_f);

        polygon.AddPoint(0.0, 0.0);
        GeodesicData firstEdge = earth.Inverse(0.0, 0.0, 0.0, 1.0);
        PolygonResult firstTestPoint = polygon.TestPoint(0.0, 1.0, false, true);
        assertThat(firstTestPoint.num).isEqualTo(2);
        assertThat(firstTestPoint.perimeter).isPositive();

        polygon.AddPoint(0.0, 1.0);
        GeodesicData edge = earth.Inverse(0.0, 1.0, 1.0, 1.0);
        PolygonResult testEdge = polygon.TestEdge(edge.azi1, edge.s12, false, true);
        polygon.AddEdge(edge.azi1, edge.s12);
        PolygonResult triangle = polygon.Compute();

        assertThat(testEdge.num).isEqualTo(triangle.num);
        assertThat(testEdge.perimeter).isCloseTo(triangle.perimeter, within(DISTANCE_TOLERANCE));
        assertThat(testEdge.area).isCloseTo(triangle.area, within(1.0));
        assertThat(triangle.num).isEqualTo(3);
        assertThat(triangle.area).isNotZero();

        Pair trianglePoint = polygon.CurrentPoint();
        assertThat(trianglePoint.first).isCloseTo(1.0, within(ANGULAR_TOLERANCE));
        assertThat(trianglePoint.second).isCloseTo(1.0, within(ANGULAR_TOLERANCE));

        PolygonResult testPoint = polygon.TestPoint(1.0, 0.0, false, true);
        polygon.AddPoint(1.0, 0.0);
        PolygonResult complete = polygon.Compute();
        PolygonResult reversed = polygon.Compute(true, true);
        PolygonResult unsigned = polygon.Compute(false, false);

        assertThat(testPoint.num).isEqualTo(complete.num);
        assertThat(testPoint.perimeter).isCloseTo(complete.perimeter, within(DISTANCE_TOLERANCE));
        assertThat(testPoint.area).isCloseTo(complete.area, within(1.0));
        assertThat(reversed.area).isCloseTo(-complete.area, within(1.0));
        assertThat(unsigned.area).isCloseTo(Math.abs(complete.area), within(1.0));

        polygon.Clear();
        PolygonResult cleared = polygon.Compute();
        assertThat(cleared.num).isZero();
        assertThat(cleared.perimeter).isZero();
        assertThat(cleared.area).isZero();

        PolygonArea polyline = new PolygonArea(earth, true);
        polyline.AddPoint(0.0, 0.0);
        polyline.AddPoint(0.0, 1.0);
        polyline.AddPoint(1.0, 1.0);
        PolygonResult polylineResult = polyline.Compute();
        assertThat(polylineResult.num).isEqualTo(3);
        assertThat(polylineResult.perimeter)
                .isCloseTo(firstEdge.s12 + edge.s12, within(DISTANCE_TOLERANCE));
        assertThat(polylineResult.area).isNaN();
        assertThat(polyline.TestPoint(1.0, 0.0, false, true).area).isNaN();
    }

    @Test
    void projectsCoordinatesWithGnomonicForwardAndReverseOperations() {
        Gnomonic projection = new Gnomonic(Geodesic.WGS84);
        double centerLatitude = 48.0 + 50.0 / 60.0;
        double centerLongitude = 2.0 + 20.0 / 60.0;
        double latitude = 50.9;
        double longitude = 1.8;

        GnomonicData projected = projection.Forward(
                centerLatitude, centerLongitude, latitude, longitude);
        GnomonicData restored = projection.Reverse(
                centerLatitude, centerLongitude, projected.x, projected.y);

        assertThat(projected.lat0).isCloseTo(centerLatitude, within(ANGULAR_TOLERANCE));
        assertThat(projected.lon0).isCloseTo(centerLongitude, within(ANGULAR_TOLERANCE));
        assertThat(projected.lat).isCloseTo(latitude, within(ANGULAR_TOLERANCE));
        assertThat(projected.lon).isCloseTo(longitude, within(ANGULAR_TOLERANCE));
        assertThat(projected.x).isFinite();
        assertThat(projected.y).isFinite();
        assertThat(projected.azi).isFinite();
        assertThat(projected.rk).isFinite();
        assertThat(restored.lat).isCloseTo(latitude, within(1.0e-9));
        assertThat(restored.lon).isCloseTo(longitude, within(1.0e-9));
        assertThat(projection.EquatorialRadius()).isEqualTo(Constants.WGS84_a);
        assertThat(projection.Flattening()).isEqualTo(Constants.WGS84_f);
    }

    @Test
    void providesStableGeographicMathUtilities() {
        Pair pair = new Pair(3.0, 4.0);
        GeoMath.norm(pair, pair.first, pair.second);
        assertThat(pair.first).isCloseTo(0.6, within(ANGULAR_TOLERANCE));
        assertThat(pair.second).isCloseTo(0.8, within(ANGULAR_TOLERANCE));
        assertThat(GeoMath.sq(3.0)).isEqualTo(9.0);
        assertThat(GeoMath.atanh(0.5)).isCloseTo(Math.log(3.0) / 2.0, within(ANGULAR_TOLERANCE));

        GeoMath.sum(pair, 1.0e16, 1.0);
        assertThat(pair.first).isEqualTo(1.0e16);
        assertThat(pair.second).isEqualTo(1.0);
        assertThat(GeoMath.polyval(2, new double[] {2.0, 3.0, 4.0}, 0, 5.0)).isEqualTo(69.0);
        assertThat(GeoMath.AngRound(0.0)).isEqualTo(0.0);
        assertThat(GeoMath.AngNormalize(540.0)).isEqualTo(180.0);
        assertThat(GeoMath.LatFix(45.0)).isEqualTo(45.0);
        assertThat(GeoMath.LatFix(91.0)).isNaN();

        GeoMath.AngDiff(pair, 170.0, -170.0);
        assertThat(pair.first).isCloseTo(20.0, within(ANGULAR_TOLERANCE));
        GeoMath.sincosd(pair, 30.0);
        assertThat(pair.first).isCloseTo(0.5, within(ANGULAR_TOLERANCE));
        assertThat(pair.second).isCloseTo(Math.sqrt(0.75), within(ANGULAR_TOLERANCE));
        GeoMath.sincosde(pair, 20.0, 0.5);
        assertThat(pair.first).isCloseTo(Math.sin(Math.toRadians(20.5)), within(ANGULAR_TOLERANCE));
        assertThat(pair.second)
                .isCloseTo(Math.cos(Math.toRadians(20.5)), within(ANGULAR_TOLERANCE));
        assertThat(GeoMath.atan2d(1.0, 1.0)).isCloseTo(45.0, within(ANGULAR_TOLERANCE));
    }

    @Test
    void accumulatesAndNormalizesFloatingPointSums() {
        Accumulator accumulator = new Accumulator(1.0);
        accumulator.Add(2.0);
        assertThat(accumulator.Sum()).isEqualTo(3.0);
        assertThat(accumulator.Sum(4.0)).isEqualTo(7.0);
        assertThat(accumulator.Sum()).isEqualTo(3.0);

        Accumulator copy = new Accumulator(accumulator);
        copy.Negate();
        assertThat(copy.Sum()).isEqualTo(-3.0);
        copy.Remainder(2.0);
        assertThat(copy.Sum()).isEqualTo(1.0);

        Pair result = new Pair();
        Accumulator.AddInternal(result, 1.0, 2.0, 3.0);
        assertThat(result.first + result.second).isEqualTo(6.0);
        accumulator.Set(10.0);
        assertThat(accumulator.Sum()).isEqualTo(10.0);
    }
}
