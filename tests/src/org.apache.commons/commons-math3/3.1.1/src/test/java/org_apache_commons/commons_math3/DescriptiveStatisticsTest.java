/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.math3.stat.descriptive.AbstractUnivariateStatistic;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.UnivariateStatistic;
import org.junit.jupiter.api.Test;

public class DescriptiveStatisticsTest {
    @Test
    void usesCustomPercentileImplementationThroughPublicSetQuantileHook() {
        DescriptiveStatistics statistics = new DescriptiveStatistics();
        statistics.addValue(1.0d);
        statistics.addValue(2.0d);
        statistics.addValue(7.0d);
        RecordingPercentileStatistic percentile = new RecordingPercentileStatistic();

        statistics.setPercentileImpl(percentile);

        assertThat(statistics.getPercentileImpl()).isSameAs(percentile);
        assertThat(percentile.getQuantile()).isEqualTo(50.0d);

        double result = statistics.getPercentile(90.0d);

        assertThat(result).isEqualTo(93.0d);
        assertThat(percentile.getQuantile()).isEqualTo(90.0d);
        assertThat(percentile.getObservationCount()).isEqualTo(3);
        assertThat(percentile.getTotal()).isEqualTo(10.0d);
    }

    public static final class RecordingPercentileStatistic extends AbstractUnivariateStatistic {
        private double quantile;
        private int observationCount;
        private double total;

        public void setQuantile(double quantile) {
            this.quantile = quantile;
        }

        public double getQuantile() {
            return quantile;
        }

        public int getObservationCount() {
            return observationCount;
        }

        public double getTotal() {
            return total;
        }

        @Override
        public double evaluate(double[] values, int begin, int length) {
            observationCount = length;
            total = 0.0d;
            for (int index = begin; index < begin + length; index++) {
                total += values[index];
            }
            return quantile + observationCount;
        }

        @Override
        public UnivariateStatistic copy() {
            RecordingPercentileStatistic copy = new RecordingPercentileStatistic();
            copy.quantile = quantile;
            copy.observationCount = observationCount;
            copy.total = total;
            return copy;
        }
    }
}
