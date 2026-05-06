/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.value.ClickHouseGeoRingValue;
import org.junit.jupiter.api.Test;

public class ClickHouseGeoRingValueTest {
    @Test
    void createsTypedArrayOfPoints() {
        double[] firstPoint = new double[] {1.0D, 2.0D };
        double[] secondPoint = new double[] {3.0D, 4.0D };
        ClickHouseGeoRingValue value = ClickHouseGeoRingValue.of(new double[][] {firstPoint, secondPoint });

        double[][] points = value.asArray(double[].class);

        assertThat(points).isInstanceOf(double[][].class);
        assertThat(points.length).isEqualTo(2);
        assertThat(points[0]).isSameAs(firstPoint);
        assertThat(points[1]).isSameAs(secondPoint);
    }
}
