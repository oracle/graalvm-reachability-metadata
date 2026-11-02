/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.example.geographiclib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.sf.geographiclib.Accumulator;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.junit.jupiter.api.Test;

/** The distance-accounting subsystem maintains a geodesic delivery mileage ledger. */
public class GeodesicDistanceLedgerTest {
    private static final double DISTANCE_TOLERANCE = 1.0e-6;

    @Test
    void totalsDeliveryLegsAndPreviewsAPlannedDetour() {
        Stop sanFrancisco = new Stop("San Francisco", 37.7749, -122.4194);
        Stop losAngeles = new Stop("Los Angeles", 34.0522, -118.2437);
        Stop lasVegas = new Stop("Las Vegas", 36.1699, -115.1398);
        Stop phoenix = new Stop("Phoenix", 33.4484, -112.0740);
        Geodesic earth = Geodesic.WGS84;
        Accumulator mileage = new Accumulator(0.0);

        GeodesicData sanFranciscoToLosAngeles = leg(earth, sanFrancisco, losAngeles);
        GeodesicData losAngelesToLasVegas = leg(earth, losAngeles, lasVegas);
        GeodesicData lasVegasToPhoenix = leg(earth, lasVegas, phoenix);
        mileage.Add(sanFranciscoToLosAngeles.s12);
        mileage.Add(losAngelesToLasVegas.s12);
        mileage.Add(lasVegasToPhoenix.s12);

        assertThat(sanFranciscoToLosAngeles.s12)
                .isCloseTo(559042.3365035715, within(DISTANCE_TOLERANCE));
        assertThat(losAngelesToLasVegas.s12)
                .isCloseTo(367748.5002979363, within(DISTANCE_TOLERANCE));
        assertThat(lasVegasToPhoenix.s12)
                .isCloseTo(412070.0935347815, within(DISTANCE_TOLERANCE));
        assertThat(mileage.Sum()).isCloseTo(1338860.9303362893, within(DISTANCE_TOLERANCE));
        assertThat(mileage.Sum(250.0)).isCloseTo(1339110.9303362893, within(DISTANCE_TOLERANCE));
        assertThat(mileage.Sum()).isCloseTo(1338860.9303362893, within(DISTANCE_TOLERANCE));
    }

    private static GeodesicData leg(Geodesic earth, Stop departure, Stop arrival) {
        return earth.Inverse(
                departure.latitude(), departure.longitude(), arrival.latitude(), arrival.longitude());
    }

    private record Stop(String name, double latitude, double longitude) {}
}
