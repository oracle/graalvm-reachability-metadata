/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.example.geographiclib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.sf.geographiclib.GeoMath;
import net.sf.geographiclib.Pair;
import org.junit.jupiter.api.Test;

/** The navigation-input subsystem normalizes telemetry coordinates and heading deltas. */
public class NavigationInputNormalizationTest {
    private static final double ANGLE_TOLERANCE = 1.0e-12;

    @Test
    void normalizesWrappedCoordinatesAndComputesShortestHeadingChange() {
        NavigationFix rawFix = new NavigationFix(90.0, 541.0, 359.0);
        NavigationFix normalizedFix = normalize(rawFix);
        Pair headingChange = new Pair();

        GeoMath.AngDiff(headingChange, normalizedFix.heading(), 1.0);

        assertThat(normalizedFix.latitude()).isCloseTo(90.0, within(ANGLE_TOLERANCE));
        assertThat(normalizedFix.longitude()).isCloseTo(-179.0, within(ANGLE_TOLERANCE));
        assertThat(normalizedFix.heading()).isCloseTo(-1.0, within(ANGLE_TOLERANCE));
        assertThat(headingChange.first).isCloseTo(2.0, within(ANGLE_TOLERANCE));
        assertThat(headingChange.second).isEqualTo(0.0);
    }

    @Test
    void rejectsCoordinatesOutsideTheLatitudeRange() {
        NavigationFix rawFix = new NavigationFix(90.5, 12.0, 180.0);

        double normalizedLatitude = GeoMath.LatFix(rawFix.latitude());

        assertThat(normalizedLatitude).isNaN();
    }

    private static NavigationFix normalize(NavigationFix rawFix) {
        return new NavigationFix(
                GeoMath.LatFix(rawFix.latitude()),
                GeoMath.AngNormalize(rawFix.longitude()),
                GeoMath.AngNormalize(rawFix.heading()));
    }

    private record NavigationFix(double latitude, double longitude, double heading) {}
}
