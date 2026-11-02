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

/** The geodesic routing subsystem plans an airline route and tracks its midpoint. */
public class GeodesicRoutePlanningTest {
    private static final double DISTANCE_TOLERANCE = 1.0e-6;
    private static final double ANGLE_TOLERANCE = 1.0e-12;

    @Test
    void plansRouteAndReportsMidpointForProgressTracking() {
        Airport departure = new Airport("JFK", 40.6413, -73.7781);
        Airport destination = new Airport("LHR", 51.4700, -0.4543);
        Geodesic earth = Geodesic.WGS84;

        GeodesicData route = earth.Inverse(
                departure.latitude(), departure.longitude(),
                destination.latitude(), destination.longitude());
        GeodesicLine routeLine = earth.InverseLine(
                departure.latitude(), departure.longitude(),
                destination.latitude(), destination.longitude());
        double midpointDistance = route.s12 / 2.0;
        GeodesicData midpoint = routeLine.Position(midpointDistance);
        GeodesicData arrival = earth.Direct(
                departure.latitude(), departure.longitude(), route.azi1, route.s12);

        assertThat(route.s12).isCloseTo(5554908.7905475, within(DISTANCE_TOLERANCE));
        assertThat(route.azi1).isCloseTo(51.38164785836946, within(ANGLE_TOLERANCE));
        assertThat(route.azi2).isCloseTo(107.98282905557903, within(ANGLE_TOLERANCE));
        assertThat(midpoint.s12).isCloseTo(midpointDistance, within(DISTANCE_TOLERANCE));
        assertThat(midpoint.lat2).isCloseTo(52.237521802212825, within(ANGLE_TOLERANCE));
        assertThat(midpoint.lon2).isCloseTo(-41.29033870001499, within(ANGLE_TOLERANCE));
        assertThat(arrival.lat2).isCloseTo(destination.latitude(), within(ANGLE_TOLERANCE));
        assertThat(arrival.lon2).isCloseTo(destination.longitude(), within(ANGLE_TOLERANCE));
    }

    private record Airport(String code, double latitude, double longitude) {}
}
