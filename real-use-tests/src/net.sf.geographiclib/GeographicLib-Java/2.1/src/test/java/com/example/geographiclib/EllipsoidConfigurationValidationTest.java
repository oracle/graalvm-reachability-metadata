/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.example.geographiclib;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeographicErr;
import org.junit.jupiter.api.Test;

/** The ellipsoid-configuration subsystem rejects unusable survey model settings. */
public class EllipsoidConfigurationValidationTest {
    @Test
    void rejectsModelSettingsWithAnInvalidAxis() {
        ModelConfiguration zeroRadius = new ModelConfiguration("zero-radius", 0.0, 0.0);
        ModelConfiguration collapsedPoles = new ModelConfiguration(
                "collapsed-poles", 6_378_137.0, 1.0);

        assertThatThrownBy(() -> createModel(zeroRadius))
                .isInstanceOf(GeographicErr.class)
                .hasMessage("Equatorial radius is not positive");
        assertThatThrownBy(() -> createModel(collapsedPoles))
                .isInstanceOf(GeographicErr.class)
                .hasMessage("Polar semi-axis is not positive");
    }

    private static Geodesic createModel(ModelConfiguration configuration) {
        return new Geodesic(configuration.equatorialRadius(), configuration.flattening());
    }

    private record ModelConfiguration(String name, double equatorialRadius, double flattening) {}
}
