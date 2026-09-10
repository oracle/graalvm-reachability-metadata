/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.kafka.streams.kstream.internals.suppress;

import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies suppression policy configuration and the time definitions used by window close behavior. */
public class ApiCoverageBatchSuppressTest {
    @Test
    void shouldConfigureEagerAndStrictBufferPolicies() {
        EagerBufferConfigImpl eager = new EagerBufferConfigImpl(5L, 100L, Map.of("retention.ms", "1000"));
        assertThat(eager.withMaxRecords(10L)).isNotNull();
        assertThat(eager.withMaxBytes(200L)).isNotNull();
        assertThat(eager.withLoggingDisabled()).isNotNull();
        assertThat(eager.withLoggingEnabled(Map.of("cleanup.policy", "compact"))).isNotNull();

        StrictBufferConfigImpl strict = new StrictBufferConfigImpl();
        assertThat(strict.withMaxRecords(10L)).isNotNull();
        assertThat(strict.withMaxBytes(200L)).isNotNull();
        assertThat(strict.withLoggingDisabled()).isNotNull();
        assertThat(strict.withLoggingEnabled(Map.of("cleanup.policy", "compact"))).isNotNull();
        assertThat(strict.shutDownWhenFull()).isNotNull();
    }

    @Test
    void shouldNameAndCompareSuppressionPolicies() {
        SuppressedInternal<Windowed<String>> first = suppressedInternal("orders");
        SuppressedInternal<Windowed<String>> same = suppressedInternal("orders");
        assertThat(first.withName("renamed")).isNotNull();
        assertThat(first).isEqualTo(same);
        assertThat(first.hashCode()).isEqualTo(same.hashCode());
        assertThat(first.toString()).contains("orders");

        FinalResultsSuppressionBuilder<Windowed<String>> finalBuilder =
                new FinalResultsSuppressionBuilder<>("final", Suppressed.BufferConfig.unbounded().withNoBound());
        assertThat(finalBuilder.withName("final-renamed")).isNotNull();
        assertThat(finalBuilder.buildFinalResultsSuppression(Duration.ofSeconds(1))).isNotNull();
        assertThat(finalBuilder.equals(finalBuilder)).isTrue();
        assertThat(finalBuilder.hashCode()).isNotZero();
    }

    @Test
    void shouldUseRecordAndWindowEndTimesForSuppression() {
        org.apache.kafka.streams.processor.ProcessorContext context =
                org_apache_kafka.kafka_streams.NativeCoverageFixtures.processorContext(77L);
        Windowed<String> window = new Windowed<>("key", new TimeWindow(10L, 20L));
        TimeDefinitions.RecordTimeDefinition<String> record = TimeDefinitions.RecordTimeDefinition.instance();
        TimeDefinitions.WindowEndTimeDefinition<Windowed<String>> end = TimeDefinitions.WindowEndTimeDefinition.instance();
        assertThat(record.time(context, "key")).isEqualTo(77L);
        assertThat(record.type()).isNotNull();
        assertThat(end.time(context, window)).isEqualTo(20L);
        assertThat(end.type()).isNotNull();
    }

    private static SuppressedInternal<Windowed<String>> suppressedInternal(String name) {
        return new SuppressedInternal<>(name, Duration.ofSeconds(2), Suppressed.BufferConfig.unbounded(),
                TimeDefinitions.WindowEndTimeDefinition.instance(), true);
    }
}
