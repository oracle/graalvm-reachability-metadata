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
import net.sf.geographiclib.Gnomonic;
import net.sf.geographiclib.GnomonicData;
import org.junit.jupiter.api.Test;

/** The gnomonic projection subsystem converts geographic map markers to a local viewport. */
public class GnomonicMapViewportTest {
    private static final double ANGULAR_TOLERANCE = 1.0e-9;
    private static final double METER_TOLERANCE = 1.0e-6;

    @Test
    void projectsMarkersAndRestoresTheirGeographicLocations() {
        Gnomonic projection = new Gnomonic(Geodesic.WGS84);
        double centerLatitude = 40.7128;
        double centerLongitude = -74.0060;
        MapMarker landmark = new MapMarker("landmark", 40.7580, -73.9855);
        MapMarker waterfront = new MapMarker("waterfront", 40.6892, -74.0445);

        GnomonicData landmarkScreen = project(projection, centerLatitude, centerLongitude, landmark);
        GnomonicData waterfrontScreen = project(
                projection, centerLatitude, centerLongitude, waterfront);

        assertThat(landmarkScreen.x).isCloseTo(1731.0648414165848, within(METER_TOLERANCE));
        assertThat(landmarkScreen.y).isCloseTo(5019.608837523749, within(METER_TOLERANCE));
        assertThat(waterfrontScreen.x).isCloseTo(-3254.372959427121, within(METER_TOLERANCE));
        assertThat(waterfrontScreen.y).isCloseTo(-2620.0232435695543, within(METER_TOLERANCE));
        assertThat(landmarkScreen.azi).isFinite();
        assertThat(landmarkScreen.rk).isPositive();

        GnomonicData restored = projection.Reverse(
                centerLatitude, centerLongitude, landmarkScreen.x, landmarkScreen.y);

        assertThat(restored.lat).isCloseTo(landmark.latitude(), within(ANGULAR_TOLERANCE));
        assertThat(restored.lon).isCloseTo(landmark.longitude(), within(ANGULAR_TOLERANCE));
        assertThat(restored.x).isCloseTo(landmarkScreen.x, within(METER_TOLERANCE));
        assertThat(restored.y).isCloseTo(landmarkScreen.y, within(METER_TOLERANCE));
    }

    private static GnomonicData project(
            Gnomonic projection, double centerLatitude, double centerLongitude, MapMarker marker) {
        return projection.Forward(
                centerLatitude, centerLongitude, marker.latitude(), marker.longitude());
    }

    private record MapMarker(String name, double latitude, double longitude) {}
}
