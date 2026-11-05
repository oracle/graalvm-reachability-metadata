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

/** The compass-vector subsystem converts corrected bearings for a navigation display. */
public class CompassVectorConversionTest {
    private static final double ANGLE_TOLERANCE = 1.0e-12;

    @Test
    void convertsCorrectedBearingToUnitVectorAndBack() {
        Pair direction = new Pair();
        GeoMath.sincosde(direction, 359.0, 2.0);

        double recoveredBearing = GeoMath.atan2d(direction.first, direction.second);

        assertThat(direction.first).isCloseTo(Math.sin(Math.toRadians(1.0)), within(ANGLE_TOLERANCE));
        assertThat(direction.second).isCloseTo(Math.cos(Math.toRadians(1.0)), within(ANGLE_TOLERANCE));
        assertThat(direction.first * direction.first + direction.second * direction.second)
                .isCloseTo(1.0, within(ANGLE_TOLERANCE));
        assertThat(recoveredBearing).isCloseTo(1.0, within(ANGLE_TOLERANCE));
    }
}
