/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_latencyutils.LatencyUtils;

import org.HdrHistogram.Histogram;
import org.LatencyUtils.LatencyStats;
import org.LatencyUtils.MovingAverageIntervalEstimator;
import org.LatencyUtils.PauseDetector;
import org.LatencyUtils.PauseDetectorListener;
import org.LatencyUtils.TimeCappedMovingAverageIntervalEstimator;
import org.LatencyUtils.TimeServices;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class LatencyUtilsTest {
    private static final long HISTOGRAM_LOWEST_LATENCY = 1L;
    private static final long HISTOGRAM_HIGHEST_LATENCY = 1_000_000_000L;
    private static final int HISTOGRAM_SIGNIFICANT_DIGITS = 3;

    @Test
    void movingAverageEstimatorRequiresFullRoundedWindowAndComputesAverageIntervals() {
        MovingAverageIntervalEstimator estimator = new MovingAverageIntervalEstimator(3);

        estimator.recordInterval(10L);
        estimator.recordInterval(20L);
        estimator.recordInterval(30L);
        assertThat(estimator.getEstimatedInterval(30L)).isEqualTo(Long.MAX_VALUE);

        estimator.recordInterval(40L);
        assertThat(estimator.getEstimatedInterval(40L)).isEqualTo(10L);
        assertThat(estimator.getEstimatedInterval(100L)).isEqualTo(30L);

        estimator.recordInterval(50L);
        assertThat(estimator.getEstimatedInterval(50L)).isEqualTo(10L);

        MovingAverageIntervalEstimator sameTimeEstimator = new MovingAverageIntervalEstimator(2);
        sameTimeEstimator.recordInterval(7L);
        sameTimeEstimator.recordInterval(7L);
        assertThat(sameTimeEstimator.getEstimatedInterval(7L)).isEqualTo(1L);
    }

    @Test
    void timeCappedEstimatorUsesOnlySamplesInsideTimeCap() {
        TimeCappedMovingAverageIntervalEstimator estimator = new TimeCappedMovingAverageIntervalEstimator(4, 25L);

        estimator.recordInterval(0L);
        estimator.recordInterval(10L);
        estimator.recordInterval(20L);
        estimator.recordInterval(30L);

        assertThat(estimator.getEstimatedInterval(30L)).isEqualTo(10L);
        assertThat(estimator.toString())
                .contains("IntervalEstimator:")
                .contains("Estimated Interval: 10")
                .contains("Time cap: 25");
        assertThat(estimator.getEstimatedInterval(1_000L)).isEqualTo(Long.MAX_VALUE);

        estimator.stop();
    }

    @Test
    void pauseDetectorNotifiesHighPriorityListenersFirstAndRemovesListeners() throws InterruptedException {
        TestPauseDetector detector = new TestPauseDetector();
        try {
            List<String> notifications = new CopyOnWriteArrayList<>();
            CountDownLatch firstPauseDelivered = new CountDownLatch(2);
            CountDownLatch highPriorityNotifications = new CountDownLatch(2);

            PauseDetectorListener highPriorityListener = (pauseLengthNsec, pauseEndTimeNsec) -> {
                notifications.add("high:" + pauseLengthNsec + "@" + pauseEndTimeNsec);
                firstPauseDelivered.countDown();
                highPriorityNotifications.countDown();
            };
            PauseDetectorListener normalPriorityListener = (pauseLengthNsec, pauseEndTimeNsec) -> {
                notifications.add("normal:" + pauseLengthNsec + "@" + pauseEndTimeNsec);
                firstPauseDelivered.countDown();
            };

            detector.addListener(normalPriorityListener);
            detector.addListener(highPriorityListener, true);
            detector.firePause(7L, 11L);

            assertThat(firstPauseDelivered.await(2L, TimeUnit.SECONDS)).isTrue();
            assertThat(notifications).containsExactly("high:7@11", "normal:7@11");

            detector.removeListener(normalPriorityListener);
            detector.firePause(13L, 17L);

            assertThat(highPriorityNotifications.await(2L, TimeUnit.SECONDS)).isTrue();
            assertThat(notifications).containsExactly("high:7@11", "normal:7@11", "high:13@17");
        } finally {
            detector.shutdown();
        }
    }

    @Test
    void timeCappedEstimatorAccountsForReportedPausesInsideItsWindow() throws InterruptedException {
        TestPauseDetector detector = new TestPauseDetector();
        TimeCappedMovingAverageIntervalEstimator estimator = new TimeCappedMovingAverageIntervalEstimator(
                4,
                100L,
                detector);
        try {
            estimator.recordInterval(0L);
            estimator.recordInterval(10L);
            estimator.recordInterval(20L);
            estimator.recordInterval(30L);
            assertThat(estimator.getEstimatedInterval(30L)).isEqualTo(10L);

            CountDownLatch pauseHandled = new CountDownLatch(1);
            detector.addListener((pauseLengthNsec, pauseEndTimeNsec) -> pauseHandled.countDown());
            detector.firePause(100L, 125L);

            assertThat(pauseHandled.await(2L, TimeUnit.SECONDS)).isTrue();
            assertThat(estimator.getEstimatedInterval(130L)).isEqualTo(10L);
            assertThat(estimator.toString())
                    .contains("Time cap: 200")
                    .contains("totalPauseTimeInWindow = 100")
                    .contains("averageInterval = 10");
        } finally {
            estimator.stop();
            detector.shutdown();
        }
    }

    @Test
    void latencyStatsUsesConfiguredDefaultPauseDetectorWhenNoneIsProvided() {
        PauseDetector previousDefaultDetector = LatencyStats.getDefaultPauseDetector();
        TestPauseDetector defaultDetector = new TestPauseDetector();
        LatencyStats builderStats = null;
        LatencyStats constructorStats = null;
        try {
            LatencyStats.setDefaultPauseDetector(defaultDetector);

            builderStats = LatencyStats.Builder.create()
                    .lowestTrackableLatency(HISTOGRAM_LOWEST_LATENCY)
                    .highestTrackableLatency(HISTOGRAM_HIGHEST_LATENCY)
                    .numberOfSignificantValueDigits(HISTOGRAM_SIGNIFICANT_DIGITS)
                    .intervalEstimatorWindowLength(2)
                    .intervalEstimatorTimeCap(TimeUnit.SECONDS.toNanos(10L))
                    .build();
            constructorStats = new LatencyStats(
                    HISTOGRAM_LOWEST_LATENCY,
                    HISTOGRAM_HIGHEST_LATENCY,
                    HISTOGRAM_SIGNIFICANT_DIGITS,
                    2,
                    TimeUnit.SECONDS.toNanos(10L),
                    null);

            assertThat(LatencyStats.getDefaultPauseDetector()).isSameAs(defaultDetector);
            assertThat(builderStats.getPauseDetector()).isSameAs(defaultDetector);
            assertThat(constructorStats.getPauseDetector()).isSameAs(defaultDetector);
        } finally {
            if (builderStats != null) {
                builderStats.stop();
            }
            if (constructorStats != null) {
                constructorStats.stop();
            }
            LatencyStats.setDefaultPauseDetector(previousDefaultDetector);
            defaultDetector.shutdown();
        }
    }

    @Test
    void latencyStatsRecordsIntervalsCopiesHistogramsAndResetsIntervalCounts() {
        TestPauseDetector detector = new TestPauseDetector();
        LatencyStats stats = LatencyStats.Builder.create()
                .lowestTrackableLatency(HISTOGRAM_LOWEST_LATENCY)
                .highestTrackableLatency(HISTOGRAM_HIGHEST_LATENCY)
                .numberOfSignificantValueDigits(HISTOGRAM_SIGNIFICANT_DIGITS)
                .intervalEstimatorWindowLength(2)
                .intervalEstimatorTimeCap(TimeUnit.SECONDS.toNanos(10L))
                .pauseDetector(detector)
                .build();
        try {
            assertThat(stats.getPauseDetector()).isSameAs(detector);
            assertThat(stats.getIntervalEstimator()).isInstanceOf(TimeCappedMovingAverageIntervalEstimator.class);

            stats.recordLatency(10L);
            stats.recordLatency(20L);
            stats.recordLatency(20L);

            Histogram firstInterval = stats.getIntervalHistogram();
            assertThat(firstInterval.getTotalCount()).isEqualTo(3L);
            assertThat(firstInterval.getCountAtValue(10L)).isEqualTo(1L);
            assertThat(firstInterval.getCountAtValue(20L)).isEqualTo(2L);
            assertThat(firstInterval.getMaxValue()).isEqualTo(20L);

            Histogram latestUncorrected = stats.getLatestUncorrectedIntervalHistogram();
            assertThat(latestUncorrected.getTotalCount()).isEqualTo(3L);
            assertThat(latestUncorrected.getCountAtValue(20L)).isEqualTo(2L);

            assertThat(stats.getIntervalHistogram().getTotalCount()).isZero();

            stats.recordLatency(30L);
            Histogram target = new Histogram(
                    HISTOGRAM_LOWEST_LATENCY,
                    HISTOGRAM_HIGHEST_LATENCY,
                    HISTOGRAM_SIGNIFICANT_DIGITS);
            stats.getIntervalHistogramInto(target);
            assertThat(target.getTotalCount()).isEqualTo(1L);
            assertThat(target.getCountAtValue(30L)).isEqualTo(1L);

            stats.recordLatency(40L);
            Histogram accumulated = new Histogram(
                    HISTOGRAM_LOWEST_LATENCY,
                    HISTOGRAM_HIGHEST_LATENCY,
                    HISTOGRAM_SIGNIFICANT_DIGITS);
            accumulated.recordValue(5L);
            stats.addIntervalHistogramTo(accumulated);
            assertThat(accumulated.getTotalCount()).isEqualTo(2L);
            assertThat(accumulated.getCountAtValue(5L)).isEqualTo(1L);
            assertThat(accumulated.getCountAtValue(40L)).isEqualTo(1L);
        } finally {
            stats.stop();
            detector.shutdown();
        }
    }

    @Test
    void latencyStatsAddsPauseCorrectionValuesWhenDetectorReportsPause() throws InterruptedException {
        TestPauseDetector detector = new TestPauseDetector();
        LatencyStats stats = LatencyStats.Builder.create()
                .lowestTrackableLatency(HISTOGRAM_LOWEST_LATENCY)
                .highestTrackableLatency(HISTOGRAM_HIGHEST_LATENCY)
                .numberOfSignificantValueDigits(HISTOGRAM_SIGNIFICANT_DIGITS)
                .intervalEstimatorWindowLength(2)
                .intervalEstimatorTimeCap(TimeUnit.SECONDS.toNanos(10L))
                .pauseDetector(detector)
                .build();
        try {
            stats.recordLatency(100L);
            TimeServices.sleepNanos(TimeUnit.MILLISECONDS.toNanos(1L));
            stats.recordLatency(100L);

            long estimatedInterval = stats.getIntervalEstimator().getEstimatedInterval(TimeServices.nanoTime());
            assertThat(estimatedInterval).isPositive().isLessThan(Long.MAX_VALUE);

            CountDownLatch pauseHandledByStats = new CountDownLatch(1);
            detector.addListener((pauseLengthNsec, pauseEndTimeNsec) -> pauseHandledByStats.countDown());

            long pauseLength = Math.max(estimatedInterval * 5L, TimeUnit.MILLISECONDS.toNanos(10L));
            detector.firePause(pauseLength, TimeServices.nanoTime() + pauseLength);

            assertThat(pauseHandledByStats.await(2L, TimeUnit.SECONDS)).isTrue();

            Histogram correctedInterval = stats.getIntervalHistogram();
            Histogram uncorrectedInterval = stats.getLatestUncorrectedIntervalHistogram();
            assertThat(uncorrectedInterval.getTotalCount()).isEqualTo(2L);
            assertThat(correctedInterval.getTotalCount()).isGreaterThanOrEqualTo(uncorrectedInterval.getTotalCount());
            assertThat(correctedInterval.getCountAtValue(100L)).isEqualTo(2L);
            assertThat(correctedInterval.getMaxValue()).isGreaterThanOrEqualTo(uncorrectedInterval.getMaxValue());
        } finally {
            stats.stop();
            detector.shutdown();
        }
    }

    private static final class TestPauseDetector extends PauseDetector {
        void firePause(long pauseLengthNsec, long pauseEndTimeNsec) {
            notifyListeners(pauseLengthNsec, pauseEndTimeNsec);
        }
    }
}
