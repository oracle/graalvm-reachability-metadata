/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math.stat.descriptive.UnivariateStatistic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DescriptiveStatisticsTest {
    @Test
    void customPercentileImplementationReceivesRequestedQuantile() {
        DescriptiveStatistics statistics = new DescriptiveStatistics();
        statistics.addValue(2.0);
        statistics.addValue(4.0);
        statistics.addValue(8.0);
        QuantileAwareStatistic percentile = new QuantileAwareStatistic();

        statistics.setPercentileImpl(percentile);
        assertThat(percentile.getQuantile()).isEqualTo(50.0);

        double result = statistics.getPercentile(75.0);

        assertThat(percentile.getQuantile()).isEqualTo(75.0);
        assertThat(result).isEqualTo(89.0);
    }

    public static final class QuantileAwareStatistic implements UnivariateStatistic {
        private double quantile;

        public void setQuantile(double quantile) {
            this.quantile = quantile;
        }

        public double getQuantile() {
            return quantile;
        }

        @Override
        public double evaluate(double[] values) {
            return evaluate(values, 0, values.length);
        }

        @Override
        public double evaluate(double[] values, int begin, int length) {
            double sum = 0.0;
            for (int index = begin; index < begin + length; index++) {
                sum += values[index];
            }
            return quantile + sum;
        }

        @Override
        public UnivariateStatistic copy() {
            QuantileAwareStatistic copy = new QuantileAwareStatistic();
            copy.setQuantile(quantile);
            return copy;
        }
    }
}
