/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.metrics_spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
    private static final AtomicInteger METRIC_COUNTER = new AtomicInteger();

    @Test
    void sdkMetricCreatedWithVarargsExposesNameTypeLevelAndCategories() {
        SdkMetric<Integer> metric = SdkMetric.create(
                uniqueMetricName("request-count"),
                Integer.class,
                MetricLevel.INFO,
                MetricCategory.CORE,
                MetricCategory.HTTP_CLIENT,
                MetricCategory.CORE);

        assertThat(metric.name()).startsWith("request-count-");
        assertThat(metric.valueClass()).isEqualTo(Integer.class);
        assertThat(metric.level()).isEqualTo(MetricLevel.INFO);
        assertThat(metric.categories()).containsExactlyInAnyOrder(MetricCategory.CORE, MetricCategory.HTTP_CLIENT);
        assertThatThrownBy(() -> metric.categories().add(MetricCategory.ALL))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(metric.toString()).contains(metric.name(), "CORE", "HTTP_CLIENT");
    }

    @Test
    void sdkMetricCreatedWithCategorySetDefensivelyCopiesCategories() {
        EnumSet<MetricCategory> categories = EnumSet.of(MetricCategory.CUSTOM);
        SdkMetric<String> metric = SdkMetric.create(
                uniqueMetricName("operation-name"),
                String.class,
                MetricLevel.TRACE,
                categories);

        categories.add(MetricCategory.ALL);

        assertThat(metric.categories()).containsExactly(MetricCategory.CUSTOM);
        assertThat(metric.valueClass()).isEqualTo(String.class);
        assertThat(metric.level()).isEqualTo(MetricLevel.TRACE);
    }

    @Test
    void sdkMetricCreatedWithNullAdditionalVarargsUsesOnlyPrimaryCategory() {
        SdkMetric<Long> metric = SdkMetric.create(
                uniqueMetricName("single-category"),
                Long.class,
                MetricLevel.ERROR,
                MetricCategory.CUSTOM,
                (MetricCategory[]) null);

        assertThat(metric.categories()).containsExactly(MetricCategory.CUSTOM);
    }

    @Test
    void sdkMetricEqualityHashCodeAndStringAreBasedOnPublicMetricIdentity() {
        SdkMetric<Integer> first = SdkMetric.create(
                uniqueMetricName("identity-one"), Integer.class, MetricLevel.INFO, MetricCategory.CORE);
        SdkMetric<Integer> second = SdkMetric.create(
                uniqueMetricName("identity-two"), Integer.class, MetricLevel.INFO, MetricCategory.CORE);

        assertThat(first).isEqualTo(first);
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo(null);
        assertThat(first).isNotEqualTo("not-a-metric");
        assertThat(first.hashCode()).isEqualTo(first.name().hashCode());
        assertThat(first.toString()).contains("DefaultMetric", first.name(), "categories");
    }

    @Test
    void sdkMetricRejectsInvalidDefinitionsAndDuplicateNames() {
        String duplicateName = uniqueMetricName("duplicate");
        SdkMetric.create(duplicateName, Integer.class, MetricLevel.INFO, MetricCategory.CUSTOM);

        assertThatThrownBy(() -> SdkMetric.create(" ", Integer.class, MetricLevel.INFO, MetricCategory.CUSTOM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> SdkMetric.create(
                uniqueMetricName("missing-type"), null, MetricLevel.INFO, MetricCategory.CUSTOM))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SdkMetric.create(
                uniqueMetricName("missing-level"), Integer.class, null, MetricCategory.CUSTOM))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("level");
        assertThatThrownBy(() -> SdkMetric.create(
                uniqueMetricName("empty-categories"), Integer.class, MetricLevel.INFO, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("categories");
        assertThatThrownBy(() -> SdkMetric.create(
                uniqueMetricName("null-category"), Integer.class, MetricLevel.INFO, categoriesWithNullElement()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("categories");
        assertThatThrownBy(() -> SdkMetric.create(duplicateName, Long.class, MetricLevel.ERROR, MetricCategory.ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(duplicateName);
    }

    @Test
    void metricCategoryConvertsFromPublishedValuesCaseInsensitively() {
        assertThat(MetricCategory.CORE.getValue()).isEqualTo("Core");
        assertThat(MetricCategory.HTTP_CLIENT.getValue()).isEqualTo("HttpClient");
        assertThat(MetricCategory.CUSTOM.getValue()).isEqualTo("Custom");
        assertThat(MetricCategory.ALL.getValue()).isEqualTo("All");

        assertThat(MetricCategory.fromString("core")).isEqualTo(MetricCategory.CORE);
        assertThat(MetricCategory.fromString("HTTPCLIENT")).isEqualTo(MetricCategory.HTTP_CLIENT);
        assertThat(MetricCategory.fromString("custom")).isEqualTo(MetricCategory.CUSTOM);
        assertThat(MetricCategory.fromString("all")).isEqualTo(MetricCategory.ALL);
        assertThatThrownBy(() -> MetricCategory.fromString("transport"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transport");
        assertThatThrownBy(() -> MetricCategory.fromString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void metricLevelIncludesLowerVerbosityLevels() {
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
    void metricCollectorCollectsMultipleValuesAndChildCollections() {
        SdkMetric<Integer> attemptCount = SdkMetric.create(
                uniqueMetricName("attempt-count"), Integer.class, MetricLevel.INFO, MetricCategory.CORE);
        SdkMetric<String> status = SdkMetric.create(
                uniqueMetricName("status"), String.class, MetricLevel.ERROR, MetricCategory.CUSTOM);
        MetricCollector collector = MetricCollector.create("client-call");
        Instant beforeCollecting = Instant.now();

        collector.reportMetric(attemptCount, 1);
        collector.reportMetric(attemptCount, 2);
        MetricCollector retryChild = collector.createChild("retry");
        retryChild.reportMetric(status, "throttled");
        MetricCollector httpChild = collector.createChild("http");
        httpChild.reportMetric(status, "ok");
        MetricCollection collection = collector.collect();
        Instant afterCollecting = Instant.now();

        assertThat(collector.name()).isEqualTo("client-call");
        assertThat(collector.toString()).contains("DefaultMetricCollector", attemptCount.name());
        assertThat(retryChild.name()).isEqualTo("retry");
        assertThat(httpChild.name()).isEqualTo("http");
        assertThat(collection.name()).isEqualTo("client-call");
        assertThat(collection.creationTime()).isBetween(beforeCollecting, afterCollecting);
        assertThat(collection.metricValues(attemptCount)).containsExactly(1, 2);
        assertThat(collection.metricValues(status)).isEmpty();
        assertThat(collection.children()).extracting(MetricCollection::name).containsExactly("retry", "http");
        assertThat(collection.childrenWithName("retry")).singleElement().satisfies(child -> {
            assertThat(child.name()).isEqualTo("retry");
            assertThat(child.metricValues(status)).containsExactly("throttled");
        });
        assertThat(collection.childrenWithName("missing")).isEmpty();
        assertThat(collection.toString()).contains("client-call", attemptCount.name(), "children");
    }

    @Test
    void metricCollectorRecursivelyCollectsNestedChildCollections() {
        SdkMetric<String> phase = SdkMetric.create(
                uniqueMetricName("phase"), String.class, MetricLevel.INFO, MetricCategory.CORE);
        MetricCollector root = MetricCollector.create("root-call");
        MetricCollector attempt = root.createChild("attempt");
        MetricCollector signer = attempt.createChild("signer");
        MetricCollector marshaller = attempt.createChild("marshaller");

        signer.reportMetric(phase, "signed");
        marshaller.reportMetric(phase, "marshalled");
        MetricCollection collection = root.collect();

        assertThat(collection.children()).singleElement().satisfies(attemptCollection -> {
            assertThat(attemptCollection.name()).isEqualTo("attempt");
            assertThat(attemptCollection.metricValues(phase)).isEmpty();
            assertThat(attemptCollection.children()).extracting(MetricCollection::name)
                    .containsExactly("signer", "marshaller");
            assertThat(attemptCollection.childrenWithName("signer")).singleElement()
                    .satisfies(child -> assertThat(child.metricValues(phase)).containsExactly("signed"));
            assertThat(attemptCollection.childrenWithName("marshaller")).singleElement()
                    .satisfies(child -> assertThat(child.metricValues(phase)).containsExactly("marshalled"));
        });
        assertThat(collection.childrenWithName("signer")).isEmpty();
    }

    @Test
    void metricCollectionReturnsAllDirectChildrenWithMatchingName() {
        SdkMetric<String> outcome = SdkMetric.create(
                uniqueMetricName("attempt-outcome"), String.class, MetricLevel.INFO, MetricCategory.CORE);
        MetricCollector root = MetricCollector.create("duplicate-child-root");
        MetricCollector firstAttempt = root.createChild("attempt");
        MetricCollector secondAttempt = root.createChild("attempt");
        MetricCollector operation = root.createChild("operation");

        firstAttempt.reportMetric(outcome, "throttled");
        secondAttempt.reportMetric(outcome, "success");
        operation.reportMetric(outcome, "complete");
        firstAttempt.createChild("attempt").reportMetric(outcome, "nested");
        MetricCollection collection = root.collect();

        List<MetricCollection> matchingAttempts = collection.childrenWithName("attempt")
                .collect(Collectors.toList());

        assertThat(matchingAttempts).hasSize(2);
        assertThat(matchingAttempts).extracting(MetricCollection::name).containsExactly("attempt", "attempt");
        assertThat(matchingAttempts).extracting(child -> child.metricValues(outcome))
                .containsExactly(List.of("throttled"), List.of("success"));
        assertThat(matchingAttempts.get(0).childrenWithName("attempt")).singleElement()
                .satisfies(child -> assertThat(child.metricValues(outcome)).containsExactly("nested"));
    }

    @Test
    void metricCollectionStreamsAndIteratesOverCollectedMetricRecords() {
        SdkMetric<Integer> bytes = SdkMetric.create(
                uniqueMetricName("bytes"), Integer.class, MetricLevel.TRACE, MetricCategory.HTTP_CLIENT);
        SdkMetric<String> endpoint = SdkMetric.create(
                uniqueMetricName("endpoint"), String.class, MetricLevel.INFO, MetricCategory.CUSTOM);
        MetricCollector collector = MetricCollector.create("http-request");

        collector.reportMetric(bytes, 128);
        collector.reportMetric(endpoint, "example.com");
        MetricCollection collection = collector.collect();

        List<SdkMetric<?>> actualMetrics = collection.stream()
                .map(MetricRecord::metric)
                .collect(Collectors.toList());
        List<Object> actualValues = collection.stream()
                .map(MetricRecord::value)
                .collect(Collectors.toList());
        List<Object> iteratedValues = collection.stream()
                .map(MetricRecord::value)
                .collect(Collectors.toList());
        List<SdkMetric<?>> expectedMetrics = List.of(bytes, endpoint);
        List<Object> expectedValues = List.of(128, "example.com");

        assertThat(actualMetrics).containsExactlyInAnyOrderElementsOf(expectedMetrics);
        assertThat(actualValues).containsExactlyInAnyOrderElementsOf(expectedValues);
        assertThat(iteratedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    void metricCollectionSupportsDirectIterableTraversal() {
        SdkMetric<Integer> responseSize = SdkMetric.create(
                uniqueMetricName("response-size"), Integer.class, MetricLevel.INFO, MetricCategory.HTTP_CLIENT);
        SdkMetric<String> serviceId = SdkMetric.create(
                uniqueMetricName("service-id"), String.class, MetricLevel.INFO, MetricCategory.CORE);
        MetricCollector collector = MetricCollector.create("iterable-collection");

        collector.reportMetric(responseSize, 512);
        collector.reportMetric(serviceId, "s3");
        MetricCollection collection = collector.collect();
        List<SdkMetric<?>> iteratedMetrics = new ArrayList<>();
        List<Object> iteratedValues = new ArrayList<>();

        for (MetricRecord<?> record : collection) {
            iteratedMetrics.add(record.metric());
            iteratedValues.add(record.value());
            assertThat(record.toString()).contains(record.metric().name(), String.valueOf(record.value()));
        }

        assertThat(iteratedMetrics).containsExactlyInAnyOrder(responseSize, serviceId);
        assertThat(iteratedValues).containsExactlyInAnyOrder(512, "s3");
    }

    @Test
    void metricCollectionPreservesNullMetricValues() {
        SdkMetric<String> nullableMetric = SdkMetric.create(
                uniqueMetricName("nullable"), String.class, MetricLevel.TRACE, MetricCategory.CUSTOM);
        MetricCollector collector = MetricCollector.create("nullable-collection");

        collector.reportMetric(nullableMetric, null);
        MetricCollection collection = collector.collect();

        assertThat(collection.metricValues(nullableMetric)).containsExactly((String) null);
        assertThat(collection.stream()).singleElement().satisfies(record -> {
            assertThat(record.metric()).isEqualTo(nullableMetric);
            assertThat(record.value()).isNull();
        });
    }

    @Test
    void collectedMetricValuesAndChildrenAreUnmodifiableViews() {
        SdkMetric<Integer> metric = SdkMetric.create(
                uniqueMetricName("immutable"), Integer.class, MetricLevel.INFO, MetricCategory.CUSTOM);
        MetricCollector collector = MetricCollector.create("immutable-collector");
        collector.reportMetric(metric, 42);
        collector.createChild("child");
        MetricCollection collection = collector.collect();

        List<Integer> metricValues = collection.metricValues(metric);
        List<MetricCollection> children = collection.children();

        assertThat(metricValues).containsExactly(42);
        assertThatThrownBy(() -> metricValues.add(43)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> children.add(collection)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void collectedMetricCollectionKeepsChildSnapshotIndependentFromCollector() {
        MetricCollector collector = MetricCollector.create("snapshot-collector");
        collector.createChild("collected-child");
        MetricCollection collection = collector.collect();

        collector.createChild("late-child");

        assertThat(collection.children()).extracting(MetricCollection::name).containsExactly("collected-child");
        assertThat(collection.childrenWithName("late-child")).isEmpty();
    }

    @Test
    void noOpMetricCollectorIgnoresMetricsAndReturnsEmptyCollection() {
        SdkMetric<Integer> metric = SdkMetric.create(
                uniqueMetricName("ignored"), Integer.class, MetricLevel.INFO, MetricCategory.CUSTOM);
        NoOpMetricCollector collector = NoOpMetricCollector.create();

        collector.reportMetric(metric, 99);
        MetricCollector child = collector.createChild("ignored-child");
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
    void loggingMetricPublisherPublishesPlainAndPrettyCollections() {
        SdkMetric<Integer> latency = SdkMetric.create(
                uniqueMetricName("latency"), Integer.class, MetricLevel.INFO, MetricCategory.CORE);
        MetricCollector collector = MetricCollector.create("publisher-root");
        collector.reportMetric(latency, 15);
        collector.createChild("publisher-child").reportMetric(latency, 20);
        MetricCollection collection = collector.collect();

        MetricPublisher defaultPublisher = LoggingMetricPublisher.create();
        MetricPublisher plainPublisher = LoggingMetricPublisher.create(
                Level.INFO, LoggingMetricPublisher.Format.PLAIN);
        MetricPublisher prettyPublisher = LoggingMetricPublisher.create(
                Level.INFO, LoggingMetricPublisher.Format.PRETTY);
        MetricPublisher disabledTracePublisher = LoggingMetricPublisher.create(
                Level.TRACE, LoggingMetricPublisher.Format.PLAIN);

        defaultPublisher.publish(collection);
        plainPublisher.publish(collection);
        prettyPublisher.publish(collection);
        disabledTracePublisher.publish(collection);
        defaultPublisher.close();
        plainPublisher.close();
        prettyPublisher.close();
        disabledTracePublisher.close();
    }

    @Test
    void loggingMetricPublisherPrettyPrintsEmptyCollections() {
        MetricCollection collection = MetricCollector.create("empty-publisher-root").collect();
        MetricPublisher prettyPublisher = LoggingMetricPublisher.create(
                Level.INFO, LoggingMetricPublisher.Format.PRETTY);

        prettyPublisher.publish(collection);
        prettyPublisher.close();
    }

    @Test
    void loggingMetricPublisherRejectsNullConfiguration() {
        assertThatThrownBy(() -> LoggingMetricPublisher.create(null, LoggingMetricPublisher.Format.PLAIN))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("logLevel");
        assertThatThrownBy(() -> LoggingMetricPublisher.create(Level.INFO, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("format");
    }

    @Test
    void metricCollectorRejectsBlankRootName() {
        assertThatThrownBy(() -> MetricCollector.create(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> MetricCollector.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    private static Set<MetricCategory> categoriesWithNullElement() {
        Set<MetricCategory> categories = new HashSet<>();
        categories.add(MetricCategory.CUSTOM);
        categories.add(null);
        return categories;
    }

    private static String uniqueMetricName(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + METRIC_COUNTER.incrementAndGet();
    }
}
