/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_orbisgis.cts;

import static org.assertj.core.api.Assertions.assertThat;

import org.cts.datum.GeodeticDatum;
import org.cts.op.transformation.Altitude2EllipsoidalHeight;
import org.junit.jupiter.api.Test;

public class Altitude2EllipsoidalHeightTest {
    @Test
    void appliesVerticalGridOffsetToEllipsoidalHeight() throws Exception {
        Altitude2EllipsoidalHeight transformation =
                new Altitude2EllipsoidalHeight("/vertical-grid.txt", GeodeticDatum.WGS84);
        double[] coordinate = {0.5, 0.5, 100.0};

        double[] transformed = transformation.transform(coordinate);

        assertThat(transformation.getAssociatedDatum()).isSameAs(GeodeticDatum.WGS84);
        assertThat(transformation.getGridFileName()).isEqualTo("/vertical-grid.txt");
        assertThat(transformed).containsExactly(0.5, 0.5, 101.0);
        assertThat(coordinate).containsExactly(0.5, 0.5, 101.0);
    }
}
