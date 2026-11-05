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
import net.sf.geographiclib.GeodesicMask;
import org.junit.jupiter.api.Test;

/** The geodesic sensitivity subsystem evaluates a survey leg's error metrics. */
public class GeodesicSensitivityAnalysisTest {
    private static final double ANGLE_TOLERANCE = 1.0e-12;
    private static final double DISTANCE_TOLERANCE = 1.0e-6;
    private static final double AREA_TOLERANCE = 1.0;

    @Test
    void estimatesSurveyLegSensitivityFromCompleteGeodesicResults() {
        SurveyPoint departure = new SurveyPoint("Los Angeles", 34.0522, -118.2437);
        SurveyPoint destination = new SurveyPoint("Tokyo", 35.6762, 139.6503);
        Geodesic earth = Geodesic.WGS84;

        GeodesicData leg = earth.Inverse(
                departure.latitude(), departure.longitude(),
                destination.latitude(), destination.longitude(), GeodesicMask.ALL);
        double oneMilliradianCrossTrackError = leg.m12 * 0.001;

        assertThat(leg.s12).isCloseTo(8838470.245270794, within(DISTANCE_TOLERANCE));
        assertThat(leg.a12).isCloseTo(79.53848594257516, within(ANGLE_TOLERANCE));
        assertThat(leg.azi1).isCloseTo(-53.90715354332252, within(ANGLE_TOLERANCE));
        assertThat(leg.azi2).isCloseTo(-124.50195666488244, within(ANGLE_TOLERANCE));
        assertThat(leg.m12).isCloseTo(6269358.09703903, within(DISTANCE_TOLERANCE));
        assertThat(leg.M12).isCloseTo(0.18367779481209706, within(ANGLE_TOLERANCE));
        assertThat(leg.M21).isCloseTo(0.1837871907512669, within(ANGLE_TOLERANCE));
        assertThat(leg.S12).isCloseTo(-4.9895697005425305E13, within(AREA_TOLERANCE));
        assertThat(oneMilliradianCrossTrackError)
                .isCloseTo(6269.35809703903, within(DISTANCE_TOLERANCE));
    }

    private record SurveyPoint(String name, double latitude, double longitude) {}
}
