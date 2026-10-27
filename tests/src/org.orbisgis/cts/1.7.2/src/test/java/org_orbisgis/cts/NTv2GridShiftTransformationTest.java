/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_orbisgis.cts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.cts.op.transformation.NTv2GridShiftTransformation;
import org.junit.jupiter.api.Test;

public class NTv2GridShiftTransformationTest {
    @Test
    void transformsCoordinatesUsingClasspathGrid() throws Exception {
        NTv2GridShiftTransformation transformation =
                NTv2GridShiftTransformation.createNTv2GridShiftTransformation("/ntv2-test.gsb");
        double[] coordinate = {Math.toRadians(0.5), Math.toRadians(0.5)};

        assertThat(transformation.isLoaded()).isFalse();
        assertThat(transformation.setMode(NTv2GridShiftTransformation.SPEED)).isTrue();
        double[] transformed = transformation.transform(coordinate);

        assertThat(transformation.isLoaded()).isTrue();
        assertThat(transformed[0]).isCloseTo(Math.toRadians(0.5 + 1.0 / 3600), within(1.0e-12));
        assertThat(transformed[1]).isCloseTo(Math.toRadians(0.5 - 2.0 / 3600), within(1.0e-12));
    }
}
