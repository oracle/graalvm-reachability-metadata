/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.Record;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises immutable Processor API records through their transformation contract. */
public class ProcessorRecordApiCoverageTest {
    @Test
    void shouldPreserveRecordFieldsAcrossImmutableTransformations() {
        Headers headers = new RecordHeaders().add("source", new byte[] {1});
        Record<String, Integer> original = new Record<>("order-1", 3, 100L, headers);
        Record<String, Integer> equivalent = new Record<>("order-1", 3, 100L, headers);

        assertThat(original.key()).isEqualTo("order-1");
        assertThat(original.value()).isEqualTo(3);
        assertThat(original.timestamp()).isEqualTo(100L);
        assertThat(original.headers()).isEqualTo(headers);
        assertThat(original).isEqualTo(equivalent);
        assertThat(original.hashCode()).isEqualTo(equivalent.hashCode());
        assertThat(original.toString()).contains("order-1", "100");

        Record<String, Integer> retimestamped = original.withTimestamp(200L);
        Record<String, String> remapped = original.withKey("order-2").withValue("ready");
        Headers replacement = new RecordHeaders().add("route", new byte[] {2});
        Record<String, Integer> rerouted = original.withHeaders(replacement);
        assertThat(retimestamped.timestamp()).isEqualTo(200L);
        assertThat(remapped.key()).isEqualTo("order-2");
        assertThat(remapped.value()).isEqualTo("ready");
        assertThat(rerouted.headers()).isEqualTo(replacement);
        assertThat(original.timestamp()).isEqualTo(100L);
        assertThat(original.value()).isEqualTo(3);
    }
}
