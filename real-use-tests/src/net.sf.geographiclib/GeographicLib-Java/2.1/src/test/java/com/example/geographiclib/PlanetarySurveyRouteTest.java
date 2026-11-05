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
import org.junit.jupiter.api.Test;

/** The custom-ellipsoid subsystem plans a rover route on a Mars reference globe. */
public class PlanetarySurveyRouteTest {
    private static final double DISTANCE_TOLERANCE = 1.0e-6;
    private static final double ANGLE_TOLERANCE = 1.0e-12;
    private static final double AREA_TOLERANCE = 1.0;

    @Test
    void plansRouteOnMarsReferenceEllipsoid() {
        Waypoint galeCrater = new Waypoint("Gale Crater", -5.4, 137.8);
        Waypoint jezeroCrater = new Waypoint("Jezero Crater", 18.4, 77.5);
        Geodesic mars = new Geodesic(3_396_190.0, 1.0 / 169.8);

        GeodesicData route = mars.Inverse(
                galeCrater.latitude(), galeCrater.longitude(),
                jezeroCrater.latitude(), jezeroCrater.longitude());
        GeodesicData arrival = mars.Direct(
                galeCrater.latitude(), galeCrater.longitude(), route.azi1, route.s12);

        assertThat(mars.EquatorialRadius()).isCloseTo(3_396_190.0, within(DISTANCE_TOLERANCE));
        assertThat(mars.Flattening()).isCloseTo(1.0 / 169.8, within(ANGLE_TOLERANCE));
        assertThat(mars.EllipsoidArea()).isCloseTo(1.443731245264502E14, within(AREA_TOLERANCE));
        assertThat(route.s12).isCloseTo(3_788_321.3929743264, within(DISTANCE_TOLERANCE));
        assertThat(route.azi1).isCloseTo(-66.71471366200406, within(ANGLE_TOLERANCE));
        assertThat(arrival.lat2).isCloseTo(jezeroCrater.latitude(), within(ANGLE_TOLERANCE));
        assertThat(arrival.lon2).isCloseTo(jezeroCrater.longitude(), within(ANGLE_TOLERANCE));
        assertThat(arrival.azi2).isCloseTo(-74.41390311855957, within(ANGLE_TOLERANCE));
    }

    private record Waypoint(String name, double latitude, double longitude) {}
}
