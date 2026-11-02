/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.example.geographiclib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.sf.geographiclib.Constants;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.junit.jupiter.api.Test;

/** The reference-ellipsoid configuration subsystem supplies the canonical WGS84 model. */
public class Wgs84ReferenceModelTest {
    private static final double ANGLE_TOLERANCE = 1.0e-12;
    private static final double DISTANCE_TOLERANCE = 1.0e-6;
    private static final double AREA_TOLERANCE = 1.0;

    @Test
    void configuresWgs84ForAnIntercontinentalRoute() {
        Geodesic earth = new Geodesic(Constants.WGS84_a, Constants.WGS84_f);

        GeodesicData route = earth.Inverse(48.8566, 2.3522, 35.6762, 139.6503);
        GeodesicData arrival = earth.Direct(48.8566, 2.3522, route.azi1, route.s12);

        assertThat(Constants.WGS84_a).isEqualTo(6_378_137.0);
        assertThat(Constants.WGS84_f)
                .isCloseTo(0.0033528106647474805, within(ANGLE_TOLERANCE));
        assertThat(earth.EquatorialRadius()).isEqualTo(Constants.WGS84_a);
        assertThat(earth.Flattening()).isEqualTo(Constants.WGS84_f);
        assertThat(earth.EllipsoidArea())
                .isCloseTo(5.1006562172408844E14, within(AREA_TOLERANCE));
        assertThat(route.s12).isCloseTo(9735308.65423938, within(DISTANCE_TOLERANCE));
        assertThat(route.azi1).isCloseTo(33.43091894673627, within(ANGLE_TOLERANCE));
        assertThat(route.azi2).isCloseTo(153.47625836941577, within(ANGLE_TOLERANCE));
        assertThat(arrival.lat2).isCloseTo(35.6762, within(ANGLE_TOLERANCE));
        assertThat(arrival.lon2).isCloseTo(139.6503, within(ANGLE_TOLERANCE));
    }
}
