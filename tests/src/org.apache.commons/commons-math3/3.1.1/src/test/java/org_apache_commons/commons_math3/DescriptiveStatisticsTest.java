/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.UnivariateStatistic;
import org.junit.jupiter.api.Test;

public class DescriptiveStatisticsTest {
    @Test
    void customPercentileImplementationIsConfiguredAndInvokedThroughPublicApi() {
        DescriptiveStatistics statistics = new DescriptiveStatistics();
        statistics.addValue(10.0d);
        statistics.addValue(20.0d);
        statistics.addValue(30.0d);
        statistics.addValue(40.0d);
        RecordingQuantileStatistic percentile = new RecordingQuantileStatistic();

        statistics.setPercentileImpl(percentile);
        double result = statistics.getPercentile(75.0d);

        assertThat(statistics.getPercentileImpl()).isSameAs(percentile);
        assertThat(percentile.getSetQuantileCalls()).isEqualTo(2);
        assertThat(percentile.getLastQuantile()).isEqualTo(75.0d);
        assertThat(percentile.getEvaluateCalls()).isEqualTo(1);
        assertThat(result).isEqualTo(100.0d);
    }

    public static final class RecordingQuantileStatistic implements UnivariateStatistic {
        private double quantile;
        private int setQuantileCalls;
        private int evaluateCalls;

        public void setQuantile(double quantile) {
            this.quantile = quantile;
            setQuantileCalls++;
        }

        @Override
        public double evaluate(double[] values) {
            return evaluate(values, 0, values.length);
        }

        @Override
        public double evaluate(double[] values, int begin, int length) {
            evaluateCalls++;
            double sum = 0.0d;
            for (int i = begin; i < begin + length; i++) {
                sum += values[i];
            }
            return sum / length + quantile;
        }

        @Override
        public UnivariateStatistic copy() {
            RecordingQuantileStatistic copy = new RecordingQuantileStatistic();
            copy.quantile = quantile;
            copy.setQuantileCalls = setQuantileCalls;
            copy.evaluateCalls = evaluateCalls;
            return copy;
        }

        private double getLastQuantile() {
            return quantile;
        }

        private int getSetQuantileCalls() {
            return setQuantileCalls;
        }

        private int getEvaluateCalls() {
            return evaluateCalls;
        }
    }
}
