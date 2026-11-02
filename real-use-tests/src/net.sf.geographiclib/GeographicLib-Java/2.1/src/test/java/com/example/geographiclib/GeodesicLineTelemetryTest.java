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
import net.sf.geographiclib.GeodesicMask;
import net.sf.geographiclib.Pair;
import org.junit.jupiter.api.Test;

/** The live-route telemetry subsystem advances a vehicle along a configured geodesic line. */
public class GeodesicLineTelemetryTest {
    private static final double DISTANCE_TOLERANCE = 1.0e-6;
    private static final double ANGLE_TOLERANCE = 1.0e-12;

    @Test
    void updatesRoutePositionFromDistanceTelemetry() {
        GeodesicLine route = Geodesic.WGS84.Line(
                34.0522, -118.2437, 45.0, GeodesicMask.ALL);
        Pair initialBearing = route.AzimuthCosines();

        route.SetDistance(1_000_000.0);
        GeodesicData firstUpdate = route.Position(route.Distance(), GeodesicMask.ALL);

        assertThat(route.Latitude()).isCloseTo(34.0522, within(ANGLE_TOLERANCE));
        assertThat(route.Longitude()).isCloseTo(-118.2437, within(ANGLE_TOLERANCE));
        assertThat(route.Azimuth()).isCloseTo(45.0, within(ANGLE_TOLERANCE));
        assertThat(initialBearing.first).isCloseTo(Math.sqrt(0.5), within(ANGLE_TOLERANCE));
        assertThat(initialBearing.second).isCloseTo(Math.sqrt(0.5), within(ANGLE_TOLERANCE));
        assertThat(firstUpdate.lat2).isCloseTo(40.15141257657294, within(ANGLE_TOLERANCE));
        assertThat(firstUpdate.lon2).isCloseTo(-109.949813682379, within(ANGLE_TOLERANCE));
        assertThat(firstUpdate.azi2).isCloseTo(50.01584761368203, within(ANGLE_TOLERANCE));
        assertThat(firstUpdate.s12).isCloseTo(1_000_000.0, within(DISTANCE_TOLERANCE));
        assertThat(route.Arc()).isCloseTo(9.002355318860694, within(ANGLE_TOLERANCE));

        route.GenSetDistance(false, 2_000_000.0);
        GeodesicData secondUpdate = route.Position(route.Distance(), GeodesicMask.ALL);

        assertThat(route.GenDistance(false)).isCloseTo(2_000_000.0, within(DISTANCE_TOLERANCE));
        assertThat(secondUpdate.lat2).isCloseTo(45.53949346549291, within(ANGLE_TOLERANCE));
        assertThat(secondUpdate.lon2).isCloseTo(-100.13154717590368, within(ANGLE_TOLERANCE));
        assertThat(secondUpdate.azi2).isCloseTo(56.708716727004166, within(ANGLE_TOLERANCE));
        assertThat(secondUpdate.s12).isCloseTo(2_000_000.0, within(DISTANCE_TOLERANCE));
    }
}
