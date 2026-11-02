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
import net.sf.geographiclib.Pair;
import net.sf.geographiclib.PolygonArea;
import net.sf.geographiclib.PolygonResult;
import org.junit.jupiter.api.Test;

/** The geodesic-track subsystem measures distance traveled by a GPS hiking trace. */
public class GeodesicTrackDistanceTest {
    private static final double METER_TOLERANCE = 1.0e-6;

    @Test
    void measuresDistanceAlongRecordedTrack() {
        PolygonArea track = new PolygonArea(Geodesic.WGS84, true);
        track.AddPoint(47.6205, -122.3493);
        track.AddPoint(47.6062, -122.3321);
        track.AddPoint(47.5952, -122.3316);
        track.AddPoint(47.5990, -122.3344);

        Pair lastFix = track.CurrentPoint();
        PolygonResult measurement = track.Compute();

        assertThat(lastFix.first).isCloseTo(47.5990, within(METER_TOLERANCE));
        assertThat(lastFix.second).isCloseTo(-122.3344, within(METER_TOLERANCE));
        assertThat(measurement.num).isEqualTo(4);
        assertThat(measurement.perimeter).isCloseTo(3745.032010041224, within(METER_TOLERANCE));
        assertThat(measurement.area).isNaN();
    }
}
