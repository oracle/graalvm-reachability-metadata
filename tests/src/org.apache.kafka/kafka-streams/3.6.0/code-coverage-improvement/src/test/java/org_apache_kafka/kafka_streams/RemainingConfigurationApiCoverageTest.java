/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.internals.StreamsConfigUtils;
import org.apache.kafka.streams.kstream.EmitStrategy;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.internals.suppress.BufferFullStrategy;
import org.apache.kafka.streams.kstream.internals.suppress.EagerBufferConfigImpl;
import org.apache.kafka.streams.kstream.internals.suppress.StrictBufferConfigImpl;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises configuration variants and public enum contracts. */
public class RemainingConfigurationApiCoverageTest {
    @Test
    void shouldConfigureBothSuppressionBufferPolicies() {
        EagerBufferConfigImpl eager = new EagerBufferConfigImpl(10L, 100L, Map.of("x", "y"));
        eager = (EagerBufferConfigImpl) eager.withMaxRecords(3).withMaxBytes(30).withLoggingDisabled();
        eager = (EagerBufferConfigImpl) eager.withLoggingEnabled(Map.of("compression.type", "lz4"));
        assertThat(eager.isLoggingEnabled()).isTrue();
        assertThat(eager.maxRecords()).isEqualTo(3L);
        assertThat(eager.maxBytes()).isEqualTo(30L);

        StrictBufferConfigImpl strict = new StrictBufferConfigImpl();
        strict = (StrictBufferConfigImpl) strict.withMaxRecords(4).withMaxBytes(40).withLoggingDisabled();
        strict = (StrictBufferConfigImpl) strict.withLoggingEnabled(new HashMap<>(Map.of("retention.ms", "1000")));
        assertThat(strict.isLoggingEnabled()).isTrue();
        assertThat(strict.bufferFullStrategy()).isEqualTo(BufferFullStrategy.SHUT_DOWN);
        assertThat(strict.maxRecords()).isEqualTo(4L);
        assertThat(strict.maxBytes()).isEqualTo(40L);
        assertThat(Suppressed.BufferConfig.unbounded()).isNotNull();
        assertThat(EmitStrategy.StrategyType.values()).contains(EmitStrategy.StrategyType.ON_WINDOW_CLOSE);
    }

    @Test
    void shouldExposeQueryParameterValueSemantics() {
        StoreQueryParameters<?> left = StoreQueryParameters.fromNameAndType("store",
                org.apache.kafka.streams.state.QueryableStoreTypes.keyValueStore());
        StoreQueryParameters<?> right = left.withPartition(null);
        assertThat(left).isEqualTo(right);
        assertThat(left.hashCode()).isEqualTo(right.hashCode());
    }

    @Test
    void shouldExposeEnumAndInternalConfigurationNames() {
        assertThat(Topology.AutoOffsetReset.values()).contains(Topology.AutoOffsetReset.EARLIEST);
        assertThat(Topology.AutoOffsetReset.valueOf("LATEST")).isEqualTo(Topology.AutoOffsetReset.LATEST);
        assertThat(DeserializationExceptionHandler.DeserializationHandlerResponse.values()).isNotEmpty();
        assertThat(ProductionExceptionHandler.ProductionExceptionHandlerResponse.values()).isNotEmpty();
        assertThat(StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.values()).isNotEmpty();
        assertThat(StreamsConfigUtils.ProcessingMode.values()).isNotEmpty();
        assertThat(StreamsConfigUtils.processingModeString(StreamsConfigUtils.ProcessingMode.EXACTLY_ONCE_V2)).isNotEmpty();
        assertThat(Materialized.StoreType.valueOf("IN_MEMORY")).isEqualTo(Materialized.StoreType.IN_MEMORY);

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "internal-config-api");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        assertThat(new StreamsConfig.InternalConfig()).isNotNull();
        assertThat(StreamsConfig.InternalConfig.getLong(Map.of("value", 12L), "value", 0L)).isEqualTo(12L);
        assertThat(StreamsConfig.InternalConfig.getLong(Map.of(), "missing", 7L)).isEqualTo(7L);
    }
}
