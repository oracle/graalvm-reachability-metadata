/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.value.ClickHouseGeoMultiPolygonValue;
import org.junit.jupiter.api.Test;

public class ClickHouseGeoMultiPolygonValueTest {
    @Test
    void createsTypedArrayOfPolygons() {
        double[][][] firstPolygon = new double[][][] {
                new double[][] { new double[] { 1.0D, 2.0D }, new double[] { 3.0D, 4.0D } } };
        double[][][] secondPolygon = new double[][][] {
                new double[][] { new double[] { 5.0D, 6.0D }, new double[] { 7.0D, 8.0D } } };
        ClickHouseGeoMultiPolygonValue value = ClickHouseGeoMultiPolygonValue.of(
                new double[][][][] { firstPolygon, secondPolygon });

        double[][][][] polygons = value.asArray(double[][][].class);

        assertThat(polygons).isInstanceOf(double[][][][].class);
        assertThat(polygons.length).isEqualTo(2);
        assertThat(polygons[0].length).isEqualTo(1);
        assertThat(polygons[0][0].length).isEqualTo(2);
        assertThat(polygons[0][0][0].length).isEqualTo(2);
        assertThat(polygons[0]).isSameAs(firstPolygon);
        assertThat(polygons[1]).isSameAs(secondPolygon);
    }
}
