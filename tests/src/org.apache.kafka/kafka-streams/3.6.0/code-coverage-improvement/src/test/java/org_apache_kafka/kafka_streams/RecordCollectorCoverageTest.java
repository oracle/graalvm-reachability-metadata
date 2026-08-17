/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.DefaultProductionExceptionHandler;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.ProcessorTopology;
import org.apache.kafka.streams.processor.internals.RecordCollectorImpl;
import org.apache.kafka.streams.processor.internals.StreamsProducer;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.test.MockClientSupplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordCollectorCoverageTest {
    @Test
    void dirtyCloseRoutesExactlyOnceCollectorThroughTransactionAbort() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "collector-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        StreamsConfig config = new StreamsConfig(properties);
        MockClientSupplier clients = new MockClientSupplier();
        clients.setApplicationIdForProducer("collector-coverage");
        LogContext logContext = new LogContext("collector-coverage ");
        TaskId taskId = new TaskId(0, 0);
        StreamsProducer producer = new StreamsProducer(config, "collector-coverage-StreamThread-1", clients, taskId,
                UUID.randomUUID(), logContext, Time.SYSTEM);
        StreamsMetricsImpl metrics = new StreamsMetricsImpl(
                new Metrics(), "coverage-thread", "coverage-process", Time.SYSTEM);
        ProcessorTopology topology = new ProcessorTopology(
                List.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of(), Set.of());
        RecordCollectorImpl collector = new RecordCollectorImpl(logContext, taskId, producer,
                new DefaultProductionExceptionHandler(), metrics, topology);

        collector.closeDirty();

        assertThat(collector.offsets()).isEmpty();
    }
}
