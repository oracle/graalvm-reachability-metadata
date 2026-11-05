/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.example.geographiclib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import net.sf.geographiclib.GeodesicLine;
import org.junit.jupiter.api.Test;

/** The arc-distance navigation subsystem tracks a flight by angular progress. */
public class GeodesicArcTrackingTest {
    private static final double ANGLE_TOLERANCE = 1.0e-12;
    private static final double DISTANCE_TOLERANCE = 1.0e-6;

    @Test
    void tracksFlightProgressByArcDistance() {
        Airport departure = new Airport("JFK", 40.6413, -73.7781);
        double initialBearing = 51.38164785836946;
        double flightArc = 50.0;
        Geodesic earth = Geodesic.WGS84;

        GeodesicData projectedArrival = earth.ArcDirect(
                departure.latitude(), departure.longitude(), initialBearing, flightArc);
        GeodesicLine flightPath = earth.ArcDirectLine(
                departure.latitude(), departure.longitude(), initialBearing, 0.0);
        GeodesicData midpoint = flightPath.ArcPosition(flightArc / 2.0);

        assertThat(projectedArrival.lat2).isCloseTo(51.46065927247449, within(ANGLE_TOLERANCE));
        assertThat(projectedArrival.lon2).isCloseTo(-0.4082776261300154, within(ANGLE_TOLERANCE));
        assertThat(projectedArrival.azi2).isCloseTo(108.01882919882976, within(ANGLE_TOLERANCE));
        assertThat(projectedArrival.s12).isCloseTo(5558271.636585799, within(DISTANCE_TOLERANCE));
        assertThat(projectedArrival.a12).isCloseTo(flightArc, within(ANGLE_TOLERANCE));

        assertThat(midpoint.lat2).isCloseTo(52.24028020675767, within(ANGLE_TOLERANCE));
        assertThat(midpoint.lon2).isCloseTo(-41.27314797301479, within(ANGLE_TOLERANCE));
        assertThat(midpoint.azi2).isCloseTo(75.35915763777294, within(ANGLE_TOLERANCE));
        assertThat(midpoint.s12).isCloseTo(2778668.17388444, within(DISTANCE_TOLERANCE));
        assertThat(midpoint.a12).isCloseTo(flightArc / 2.0, within(ANGLE_TOLERANCE));
    }

    private record Airport(String code, double latitude, double longitude) {}
}
