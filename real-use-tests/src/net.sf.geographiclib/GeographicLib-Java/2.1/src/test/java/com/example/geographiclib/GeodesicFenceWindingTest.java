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
import net.sf.geographiclib.PolygonArea;
import net.sf.geographiclib.PolygonResult;
import org.junit.jupiter.api.Test;

/** The geofence-winding subsystem preserves orientation for dateline-crossing zones. */
public class GeodesicFenceWindingTest {
    private static final double METER_TOLERANCE = 1.0e-6;

    @Test
    void reportsSignedAndUnsignedAreaForAZoneCrossingTheDateline() {
        PolygonArea zone = new PolygonArea(Geodesic.WGS84, false);
        zone.AddPoint(10.0, 179.0);
        zone.AddPoint(10.0, -179.0);
        zone.AddPoint(12.0, -179.0);
        zone.AddPoint(12.0, 179.0);

        PolygonResult signed = zone.Compute(false, true);
        PolygonResult reversed = zone.Compute(true, true);
        PolygonResult unsigned = zone.Compute(false, false);

        assertThat(signed.num).isEqualTo(4);
        assertThat(reversed.num).isEqualTo(signed.num);
        assertThat(unsigned.num).isEqualTo(signed.num);
        assertThat(signed.perimeter).isCloseTo(879542.4892393957, within(METER_TOLERANCE));
        assertThat(reversed.perimeter).isCloseTo(signed.perimeter, within(METER_TOLERANCE));
        assertThat(unsigned.perimeter).isCloseTo(signed.perimeter, within(METER_TOLERANCE));
        assertThat(signed.area).isPositive();
        assertThat(reversed.area).isCloseTo(-signed.area, within(METER_TOLERANCE));
        assertThat(unsigned.area).isCloseTo(signed.area, within(METER_TOLERANCE));
    }
}
