/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.value.ClickHouseGeoPolygonValue;
import org.junit.jupiter.api.Test;

public class ClickHouseGeoPolygonValueTest {
    @Test
    void createsTypedArrayOfRings() {
        double[][] firstRing = new double[][] { new double[] { 1.0D, 2.0D }, new double[] { 3.0D, 4.0D } };
        double[][] secondRing = new double[][] { new double[] { 5.0D, 6.0D }, new double[] { 7.0D, 8.0D } };
        ClickHouseGeoPolygonValue value = ClickHouseGeoPolygonValue.of(new double[][][] { firstRing, secondRing });

        double[][][] rings = value.asArray(double[][].class);

        assertThat(rings).isInstanceOf(double[][][].class);
        assertThat(rings.length).isEqualTo(2);
        assertThat(rings[0].length).isEqualTo(2);
        assertThat(rings[0][0].length).isEqualTo(2);
        assertThat(rings[0]).isSameAs(firstRing);
        assertThat(rings[1]).isSameAs(secondRing);
    }
}
