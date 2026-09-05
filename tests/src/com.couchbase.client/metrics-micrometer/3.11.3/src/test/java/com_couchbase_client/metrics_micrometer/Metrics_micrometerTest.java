/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.metrics_micrometer;

import com.couchbase.client.core.cnc.Counter;
import com.couchbase.client.core.cnc.ValueRecorder;
import com.couchbase.client.metrics.micrometer.MicrometerCounter;
import com.couchbase.client.metrics.micrometer.MicrometerMeter;
import com.couchbase.client.metrics.micrometer.MicrometerValueRecorder;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Metrics_micrometerTest {

    @Test
    void counterRecordsValuesAndCachesUsingFilteredTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            MicrometerMeter meter = MicrometerMeter.wrap(registry);
            Map<String, String> tags = new HashMap<>();
            tags.put("service", "key-value");
            tags.put("node", null);
            tags.put("__unit", "requests");

            Counter counter = meter.counter("couchbase.operations", tags);
            Counter equivalentCounter = meter.counter(
                    "couchbase.operations", Map.of("node", "", "service", "key-value"));
            Counter otherServiceCounter = meter.counter(
                    "couchbase.operations", Map.of("node", "", "service", "query"));

            assertThat(counter).isInstanceOf(MicrometerCounter.class);
            assertThat(equivalentCounter).isSameAs(counter);
            assertThat(otherServiceCounter).isNotSameAs(counter);

            counter.incrementBy(2);
            equivalentCounter.incrementBy(3);
            otherServiceCounter.incrementBy(7);

            assertThat(registry
                            .get("couchbase.operations")
                            .tags("node", "", "service", "key-value")
                            .counter()
                            .count())
                    .isEqualTo(5);
            assertThat(registry
                            .get("couchbase.operations")
                            .tags("node", "", "service", "key-value")
                            .counter()
                            .getId()
                            .getTag("node"))
                    .isEmpty();
            assertThat(registry
                            .get("couchbase.operations")
                            .tags("node", "", "service", "key-value")
                            .counter()
                            .getId()
                            .getTag("__unit"))
                    .isNull();
            assertThat(registry
                            .get("couchbase.operations")
                            .tags("node", "", "service", "query")
                            .counter()
                            .count())
                    .isEqualTo(7);
            assertThat(registry.find("couchbase.operations").meters()).hasSize(2);
        } finally {
            registry.close();
        }
    }

    @Test
    void valueRecorderPublishesDistributionStatisticsAndSnapshotsTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            MicrometerMeter meter = MicrometerMeter.wrap(registry);
            Map<String, String> tags = new HashMap<>();
            tags.put("bucket", "inventory");

            ValueRecorder recorder = meter.valueRecorder("couchbase.operation.duration", tags);
            tags.put("bucket", "changed-after-creation");
            ValueRecorder equivalentRecorder = meter.valueRecorder(
                    "couchbase.operation.duration", Map.of("bucket", "inventory"));
            ValueRecorder otherBucketRecorder = meter.valueRecorder(
                    "couchbase.operation.duration", Map.of("bucket", "archive"));

            assertThat(recorder).isInstanceOf(MicrometerValueRecorder.class);
            assertThat(equivalentRecorder).isSameAs(recorder);
            assertThat(otherBucketRecorder).isNotSameAs(recorder);

            recorder.recordValue(10);
            equivalentRecorder.recordValue(20);
            equivalentRecorder.recordValue(5);
            otherBucketRecorder.recordValue(40);

            DistributionSummary inventorySummary = registry
                    .get("couchbase.operation.duration")
                    .tag("bucket", "inventory")
                    .summary();
            assertThat(inventorySummary.count()).isEqualTo(3);
            assertThat(inventorySummary.totalAmount()).isEqualTo(35);
            assertThat(inventorySummary.max()).isEqualTo(20);

            DistributionSummary archiveSummary = registry
                    .get("couchbase.operation.duration")
                    .tag("bucket", "archive")
                    .summary();
            assertThat(archiveSummary.count()).isEqualTo(1);
            assertThat(archiveSummary.totalAmount()).isEqualTo(40);
            assertThat(archiveSummary.max()).isEqualTo(40);
            assertThat(registry.find("couchbase.operation.duration").meters()).hasSize(2);
        } finally {
            registry.close();
        }
    }
}
