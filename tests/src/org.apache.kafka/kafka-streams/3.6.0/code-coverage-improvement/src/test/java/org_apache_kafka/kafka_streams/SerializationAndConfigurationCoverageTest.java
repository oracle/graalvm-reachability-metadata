/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.internals.StreamsConfigUtils;
import org.apache.kafka.streams.internals.UpgradeFromValues;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler;
import org.apache.kafka.streams.kstream.EmitStrategy;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.UnlimitedWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.internals.LeftOrRightValue;
import org.apache.kafka.streams.state.internals.LeftOrRightValueSerde;
import org.apache.kafka.streams.state.internals.TimestampedKeyAndJoinSide;
import org.apache.kafka.streams.state.internals.TimestampedKeyAndJoinSideSerde;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerializationAndConfigurationCoverageTest {
    @Test
    void windowSerdesRoundTripTimeWindowedKeys() {
        Serde<Windowed<String>> serde = WindowedSerdes.timeWindowedSerdeFrom(String.class, 1000L);
        Windowed<String> original = new Windowed<>("account",
                new org.apache.kafka.streams.kstream.internals.TimeWindow(100, 200));
        byte[] bytes = serde.serializer().serialize("topic", original);
        Windowed<String> decoded = serde.deserializer().deserialize("topic", bytes);
        assertThat(decoded.key()).isEqualTo("account");
        assertThat(decoded.window().start()).isEqualTo(100);
        serde.close();

        WindowedSerdes.TimeWindowedSerde<String> configurable = new WindowedSerdes.TimeWindowedSerde<>();
        assertThat(configurable.forChangelog(true)).isSameAs(configurable);
        configurable.close();
        assertThat(new WindowedSerdes.SessionWindowedSerde<String>()).isNotNull();
        assertThat(WindowedSerdes.timeWindowedSerdeFrom(String.class)).isNotNull();
    }

    @Test
    void configurationPrefixesAndDefaultsAreExposed() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "coverage-app");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        StreamsConfig config = new StreamsConfig(properties);
        assertThat(StreamsConfig.consumerPrefix("x")).isEqualTo("consumer.x");
        assertThat(StreamsConfig.mainConsumerPrefix("x")).isEqualTo("main.consumer.x");
        assertThat(StreamsConfig.restoreConsumerPrefix("x")).isEqualTo("restore.consumer.x");
        assertThat(StreamsConfig.globalConsumerPrefix("x")).isEqualTo("global.consumer.x");
        assertThat(StreamsConfig.producerPrefix("x")).isEqualTo("producer.x");
        assertThat(StreamsConfig.adminClientPrefix("x")).isEqualTo("admin.x");
        assertThat(StreamsConfig.clientTagPrefix("x")).isEqualTo("client.tag.x");
        assertThat(StreamsConfig.configDef().names()).contains(StreamsConfig.APPLICATION_ID_CONFIG);
        assertThat(config.defaultTimestampExtractor()).isNotNull();
        assertThat(config.defaultDeserializationExceptionHandler()).isNotNull();
        assertThat(config.getGlobalConsumerConfigs("client")).containsKey("client.id");

        TopologyConfig topologyConfig = new TopologyConfig(config);
        assertThat(topologyConfig.isNamedTopology()).isFalse();
        assertThat(topologyConfig.parseStoreType()).isNotNull();
        assertThat(StreamsConfig.InternalConfig.getLong(Map.of("delay", 8L), "delay", 1L)).isEqualTo(8L);
        assertThat(new StreamsConfig.InternalConfig()).isNotNull();
        assertThat(new StreamsConfigUtils()).isNotNull();
        assertThat(StreamsConfigUtils.processingModeString(StreamsConfigUtils.ProcessingMode.AT_LEAST_ONCE))
                .isEqualTo("at_least_once");
        assertThat(StreamsConfigUtils.ProcessingMode.values()).contains(StreamsConfigUtils.ProcessingMode.AT_LEAST_ONCE);
        assertThat(StreamsConfigUtils.ProcessingMode.valueOf("AT_LEAST_ONCE"))
                .isEqualTo(StreamsConfigUtils.ProcessingMode.AT_LEAST_ONCE);
        assertThat(UpgradeFromValues.values()).isNotEmpty();
        assertThat(UpgradeFromValues.valueOf("UPGRADE_FROM_35")).isEqualTo(UpgradeFromValues.UPGRADE_FROM_35);
        assertThat(UpgradeFromValues.getValueFromString("3.5")).isEqualTo(UpgradeFromValues.UPGRADE_FROM_35);
    }

    @Test
    void timestampAndErrorPoliciesHaveDocumentedResponses() {
        FailOnInvalidTimestamp extractor = new FailOnInvalidTimestamp();
        ConsumerRecord<Object, Object> valid = new ConsumerRecord<>("topic", 0, 1, 42L,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0L, 0, 0, "k", "v");
        assertThat(extractor.extract(valid, 0)).isEqualTo(42L);
        ConsumerRecord<Object, Object> invalid = new ConsumerRecord<>("topic", 0, 1, "k", "v");
        assertThatThrownBy(() -> extractor.extract(invalid, 0)).isInstanceOf(org.apache.kafka.streams.errors.StreamsException.class);

        ConsumerRecord<byte[], byte[]> invalidBytes = new ConsumerRecord<>("topic", 0, 1,
                "k".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "v".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        LogAndContinueExceptionHandler continuing = new LogAndContinueExceptionHandler();
        continuing.configure(Map.of());
        org.apache.kafka.streams.processor.ProcessorContext context = org.mockito.Mockito.mock(
                org.apache.kafka.streams.processor.ProcessorContext.class);
        org.mockito.Mockito.when(context.taskId()).thenReturn(new org.apache.kafka.streams.processor.TaskId(0, 0));
        assertThat(continuing.handle(context, invalidBytes, new IllegalArgumentException("bad")))
                .isEqualTo(DeserializationExceptionHandler.DeserializationHandlerResponse.CONTINUE);
        assertThat(new LogAndFailExceptionHandler().handle(context, invalidBytes, new IllegalArgumentException("bad")))
                .isEqualTo(DeserializationExceptionHandler.DeserializationHandlerResponse.FAIL);
    }

    @Test
    void foreignKeyJoinSerdesDecodeBothSidesAndTimestampedKeys() {
        LeftOrRightValueSerde<String, Long> values = new LeftOrRightValueSerde<>(
                org.apache.kafka.common.serialization.Serdes.String(),
                org.apache.kafka.common.serialization.Serdes.Long());
        LeftOrRightValue<String, Long> left = LeftOrRightValue.makeLeftValue("left");
        LeftOrRightValue<String, Long> right = LeftOrRightValue.makeRightValue(42L);
        assertThat(values.deserializer().deserialize("join", values.serializer().serialize("join", left)))
                .isEqualTo(left);
        assertThat(values.deserializer().deserialize("join", values.serializer().serialize("join", right)))
                .isEqualTo(right);
        values.close();

        TimestampedKeyAndJoinSideSerde<String> keys = new TimestampedKeyAndJoinSideSerde<>(
                org.apache.kafka.common.serialization.Serdes.String());
        TimestampedKeyAndJoinSide<String> key = TimestampedKeyAndJoinSide.make(true, "account", 91L);
        assertThat(keys.deserializer().deserialize("join", keys.serializer().serialize("join", key)))
                .isEqualTo(key);
        keys.close();
    }

    @Test
    void windowAndStoreFactoriesValidateAndDescribeTheirSettings() {
        assertThat(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(5)).size()).isEqualTo(5000);
        assertThat(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(6)).inactivityGap()).isEqualTo(6000);
        assertThat(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(7)).timeDifferenceMs()).isEqualTo(7000);
        assertThat(UnlimitedWindows.of()).isNotNull();
        assertThat(EmitStrategy.onWindowClose().type()).isEqualTo(EmitStrategy.StrategyType.ON_WINDOW_CLOSE);
        assertThat(Stores.lruMap("lru", 10).name()).isEqualTo("lru");
        assertThat(Stores.persistentVersionedKeyValueStore("versions", Duration.ofMinutes(2)).name())
                .isEqualTo("versions");
        assertThat(Stores.keyValueStoreBuilder(Stores.lruMap("builder", 10),
                org.apache.kafka.common.serialization.Serdes.String(),
                org.apache.kafka.common.serialization.Serdes.String()).name()).isEqualTo("builder");
    }
}
