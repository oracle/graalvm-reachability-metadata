/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.metrics_spi;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import software.amazon.awssdk.metrics.LoggingMetricPublisher;
import software.amazon.awssdk.metrics.MetricCategory;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricCollector;
import software.amazon.awssdk.metrics.MetricLevel;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.metrics.MetricRecord;
import software.amazon.awssdk.metrics.NoOpMetricCollector;
import software.amazon.awssdk.metrics.SdkMetric;

public class Metrics_spiTest {
    private static final AtomicInteger METRIC_NAME_COUNTER = new AtomicInteger();

    @Test
    void sdkMetricCreatedWithVarargsExposesNameTypeLevelAndDistinctCategories() {
        String name = uniqueMetricName("varargs");

        SdkMetric<String> metric = SdkMetric.create(
                name,
                String.class,
                MetricLevel.INFO,
                MetricCategory.CORE,
                MetricCategory.CUSTOM,
                MetricCategory.CORE);

        assertThat(metric.name()).isEqualTo(name);
        assertThat(metric.valueClass()).isEqualTo(String.class);
        assertThat(metric.level()).isEqualTo(MetricLevel.INFO);
        assertThat(metric.categories()).containsExactlyInAnyOrder(MetricCategory.CORE, MetricCategory.CUSTOM);
        assertThat(metric.toString()).contains(name, "CORE", "CUSTOM");
        assertThatThrownBy(() -> metric.categories().add(MetricCategory.HTTP_CLIENT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sdkMetricCreatedWithSetCategoriesUsesDefensiveUnmodifiableEnumSet() {
        String name = uniqueMetricName("setCategories");
        Set<MetricCategory> categories = EnumSet.of(MetricCategory.HTTP_CLIENT, MetricCategory.CUSTOM);

        SdkMetric<Integer> metric = SdkMetric.create(name, Integer.class, MetricLevel.ERROR, categories);
        categories.clear();

        assertThat(metric.name()).isEqualTo(name);
        assertThat(metric.valueClass()).isEqualTo(Integer.class);
        assertThat(metric.level()).isEqualTo(MetricLevel.ERROR);
        assertThat(metric.categories()).containsExactlyInAnyOrder(MetricCategory.HTTP_CLIENT, MetricCategory.CUSTOM);
        assertThatThrownBy(() -> metric.categories().remove(MetricCategory.CUSTOM))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sdkMetricNamesAreUniqueAcrossValueTypesLevelsAndCategories() {
        String name = uniqueMetricName("duplicate");

        SdkMetric<String> first = SdkMetric.create(name, String.class, MetricLevel.INFO, MetricCategory.CORE);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> SdkMetric.create(name, Integer.class, MetricLevel.ERROR, MetricCategory.CUSTOM))
                .withMessageContaining(name)
                .withMessageContaining("already been created");
        assertThat(first).isEqualTo(first);
        assertThat(first.hashCode()).isEqualTo(name.hashCode());
    }

    @Test
    void sdkMetricFactoryValidatesRequiredFieldsAndCategories() {
        Set<MetricCategory> categoriesWithNull = new HashSet<>();
        categoriesWithNull.add(MetricCategory.CORE);
        categoriesWithNull.add(null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> SdkMetric.create(" ", String.class, MetricLevel.INFO, MetricCategory.CORE))
                .withMessageContaining("name");
        assertThatNullPointerException()
                .isThrownBy(() -> SdkMetric.create(
                        uniqueMetricName("nullClass"), null, MetricLevel.INFO, MetricCategory.CORE));
        assertThatNullPointerException()
                .isThrownBy(() -> SdkMetric.create(
                        uniqueMetricName("nullLevel"), String.class, null, MetricCategory.CORE))
                .withMessageContaining("level");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SdkMetric.create(
                        uniqueMetricName("emptyCategories"), String.class, MetricLevel.INFO, Set.of()))
                .withMessageContaining("categories");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SdkMetric.create(
                        uniqueMetricName("nullCategory"),
                        String.class,
                        MetricLevel.INFO,
                        categoriesWithNull))
                .withMessageContaining("categories");
    }

    @Test
    void collectorCollectsEmptyNamedCollectionWithoutReportedMetrics() {
        Instant beforeCollect = Instant.now();
        MetricCollector collector = MetricCollector.create("EmptyApiCall");

        MetricCollection collection = collector.collect();
        Instant afterCollect = Instant.now();

        assertThat(collection.name()).isEqualTo("EmptyApiCall");
        assertThat(collection.creationTime()).isBetween(beforeCollect, afterCollect);
        assertThat(collection.stream()).isEmpty();
        assertThat(collection.children()).isEmpty();
        assertThat(collection.toString()).contains("EmptyApiCall");
    }

    @Test
    void collectorCollectsMultipleTypedMetricValuesAndRecords() {
        SdkMetric<String> operationName = SdkMetric.create(
                uniqueMetricName("operationName"), String.class, MetricLevel.INFO, MetricCategory.CORE);
        SdkMetric<Integer> retryCount = SdkMetric.create(
                uniqueMetricName("retryCount"), Integer.class, MetricLevel.ERROR, MetricCategory.CORE);
        SdkMetric<Boolean> missingMetric = SdkMetric.create(
                uniqueMetricName("missing"), Boolean.class, MetricLevel.TRACE, MetricCategory.CUSTOM);
        MetricCollector collector = MetricCollector.create("ApiCall");
        Instant beforeCollect;

        collector.reportMetric(operationName, "HeadObject");
        collector.reportMetric(retryCount, 0);
        collector.reportMetric(operationName, "GetObject");
        beforeCollect = Instant.now();
        MetricCollection collection = collector.collect();
        Instant afterCollect = Instant.now();

        assertThat(collector.name()).isEqualTo("ApiCall");
        assertThat(collection.name()).isEqualTo("ApiCall");
        assertThat(collection.creationTime()).isBetween(beforeCollect, afterCollect);
        assertThat(collection.metricValues(operationName)).containsExactly("HeadObject", "GetObject");
        assertThat(collection.metricValues(retryCount)).containsExactly(0);
        assertThat(collection.metricValues(missingMetric)).isEmpty();
        assertThat(collection.children()).isEmpty();
        assertThat(collection.toString()).contains("ApiCall", operationName.name(), retryCount.name());

        List<MetricRecord<?>> records = collection.stream().collect(toList());
        assertThat(records).hasSize(3);
        assertThat(records).extracting(record -> record.metric().name())
                .containsExactlyInAnyOrder(operationName.name(), operationName.name(), retryCount.name());
        List<Object> recordValues = records.stream()
                .map(MetricRecord::value)
                .collect(toList());
        assertThat(recordValues).containsExactlyInAnyOrder("HeadObject", "GetObject", 0);
        assertThatThrownBy(() -> collection.metricValues(operationName).add("DeleteObject"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metricCollectionIsIterableOverCollectedMetricRecords() {
        SdkMetric<String> operationName = SdkMetric.create(
                uniqueMetricName("iterableOperationName"), String.class, MetricLevel.INFO, MetricCategory.CORE);
        SdkMetric<Integer> attemptCount = SdkMetric.create(
                uniqueMetricName("iterableAttemptCount"), Integer.class, MetricLevel.INFO, MetricCategory.CORE);
        MetricCollector collector = MetricCollector.create("IterableCollection");

        collector.reportMetric(operationName, "ListObjects");
        collector.reportMetric(operationName, "PutObject");
        collector.reportMetric(attemptCount, 2);
        MetricCollection collection = collector.collect();

        List<String> visitedRecords = new ArrayList<>();
        for (MetricRecord<?> record : collection) {
            visitedRecords.add(record.metric().name() + "=" + record.value());
            assertThat(record.toString()).contains(record.metric().name(), String.valueOf(record.value()));
        }

        assertThat(visitedRecords).containsExactlyInAnyOrder(
                operationName.name() + "=ListObjects",
                operationName.name() + "=PutObject",
                attemptCount.name() + "=2");
    }

    @Test
    void collectorCapturesNestedChildrenAndFiltersChildrenByName() {
        SdkMetric<Integer> statusCode = SdkMetric.create(
                uniqueMetricName("statusCode"), Integer.class, MetricLevel.ERROR, MetricCategory.HTTP_CLIENT);
        SdkMetric<String> clientName = SdkMetric.create(
                uniqueMetricName("clientName"), String.class, MetricLevel.INFO, MetricCategory.HTTP_CLIENT);
        MetricCollector root = MetricCollector.create("ApiCall");
        MetricCollector firstAttempt = root.createChild("ApiCallAttempt");
        MetricCollector secondAttempt = root.createChild("ApiCallAttempt");
        MetricCollector httpClient = firstAttempt.createChild("HttpClient");

        firstAttempt.reportMetric(statusCode, 500);
        secondAttempt.reportMetric(statusCode, 200);
        httpClient.reportMetric(clientName, "UrlConnection");
        MetricCollection collection = root.collect();

        assertThat(collection.children()).extracting(MetricCollection::name)
                .containsExactly("ApiCallAttempt", "ApiCallAttempt");
        assertThat(collection.childrenWithName("ApiCallAttempt").collect(toList())).hasSize(2);
        assertThat(collection.childrenWithName("Missing").collect(toList())).isEmpty();
        assertThatThrownBy(() -> collection.children().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        MetricCollection collectedFirstAttempt = collection.children().get(0);
        MetricCollection collectedSecondAttempt = collection.children().get(1);
        assertThat(collectedFirstAttempt.metricValues(statusCode)).containsExactly(500);
        assertThat(collectedSecondAttempt.metricValues(statusCode)).containsExactly(200);
        assertThat(collectedFirstAttempt.children()).hasSize(1);
        assertThat(collectedFirstAttempt.children().get(0).name()).isEqualTo("HttpClient");
        assertThat(collectedFirstAttempt.children().get(0).metricValues(clientName)).containsExactly("UrlConnection");
    }

    @Test
    void noOpCollectorIgnoresMetricsAndReturnsEmptyNoOpCollection() {
        SdkMetric<String> metric = SdkMetric.create(
                uniqueMetricName("noOp"), String.class, MetricLevel.INFO, MetricCategory.CUSTOM);
        NoOpMetricCollector collector = NoOpMetricCollector.create();

        collector.reportMetric(metric, "ignored");
        MetricCollector child = collector.createChild("ignored-child");
        child.reportMetric(metric, "also-ignored");
        MetricCollection collection = collector.collect();

        assertThat(NoOpMetricCollector.create()).isSameAs(collector);
        assertThat(child).isSameAs(collector);
        assertThat(collector.name()).isEqualTo("NoOp");
        assertThat(collection.name()).isEqualTo("NoOp");
        assertThat(collection.metricValues(metric)).isEmpty();
        assertThat(collection.children()).isEmpty();
        assertThat(collection.stream()).isEmpty();
        assertThat(collection.creationTime()).isNotNull();
    }

    @Test
    void metricCategoriesRoundTripFromDisplayValuesCaseInsensitively() {
        assertThat(MetricCategory.CORE.getValue()).isEqualTo("Core");
        assertThat(MetricCategory.HTTP_CLIENT.getValue()).isEqualTo("HttpClient");
        assertThat(MetricCategory.CUSTOM.getValue()).isEqualTo("Custom");
        assertThat(MetricCategory.ALL.getValue()).isEqualTo("All");

        assertThat(MetricCategory.fromString("core")).isEqualTo(MetricCategory.CORE);
        assertThat(MetricCategory.fromString("HTTPCLIENT")).isEqualTo(MetricCategory.HTTP_CLIENT);
        assertThat(MetricCategory.fromString("Custom")).isEqualTo(MetricCategory.CUSTOM);
        assertThat(MetricCategory.fromString("all")).isEqualTo(MetricCategory.ALL);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetricCategory.fromString("unknown"))
                .withMessageContaining("unknown");
    }

    @Test
    void metricLevelsIncludeLowerVerbosityLevelsOnly() {
        assertThat(MetricLevel.TRACE.includesLevel(MetricLevel.TRACE)).isTrue();
        assertThat(MetricLevel.TRACE.includesLevel(MetricLevel.INFO)).isTrue();
        assertThat(MetricLevel.TRACE.includesLevel(MetricLevel.ERROR)).isTrue();

        assertThat(MetricLevel.INFO.includesLevel(MetricLevel.TRACE)).isFalse();
        assertThat(MetricLevel.INFO.includesLevel(MetricLevel.INFO)).isTrue();
        assertThat(MetricLevel.INFO.includesLevel(MetricLevel.ERROR)).isTrue();

        assertThat(MetricLevel.ERROR.includesLevel(MetricLevel.TRACE)).isFalse();
        assertThat(MetricLevel.ERROR.includesLevel(MetricLevel.INFO)).isFalse();
        assertThat(MetricLevel.ERROR.includesLevel(MetricLevel.ERROR)).isTrue();
    }

    @Test
    void metricPublisherInterfaceAndLoggingPublisherAcceptCollectedMetrics() {
        SdkMetric<Integer> metric = SdkMetric.create(
                uniqueMetricName("publisher"), Integer.class, MetricLevel.INFO, MetricCategory.CUSTOM);
        MetricCollector collector = MetricCollector.create("PublishedCollection");
        collector.reportMetric(metric, 42);
        MetricCollection collection = collector.collect();
        RecordingMetricPublisher recordingPublisher = new RecordingMetricPublisher();

        recordingPublisher.publish(collection);
        recordingPublisher.close();
        try (LoggingMetricPublisher defaultPublisher = LoggingMetricPublisher.create();
                LoggingMetricPublisher prettyPublisher = LoggingMetricPublisher.create(
                        Level.INFO,
                        LoggingMetricPublisher.Format.PRETTY)) {
            defaultPublisher.publish(collection);
            prettyPublisher.publish(collection);
        }

        assertThat(recordingPublisher.publishedCollections()).containsExactly(collection);
        assertThat(recordingPublisher.closed()).isTrue();
    }

    private static String uniqueMetricName(String stem) {
        return Metrics_spiTest.class.getSimpleName() + "." + stem + "." + METRIC_NAME_COUNTER.incrementAndGet();
    }

    private static final class RecordingMetricPublisher implements MetricPublisher {
        private final List<MetricCollection> publishedCollections = new ArrayList<>();
        private boolean closed;

        @Override
        public void publish(MetricCollection metricCollection) {
            publishedCollections.add(metricCollection);
        }

        @Override
        public void close() {
            closed = true;
        }

        private List<MetricCollection> publishedCollections() {
            return publishedCollections;
        }

        private boolean closed() {
            return closed;
        }
    }
}
