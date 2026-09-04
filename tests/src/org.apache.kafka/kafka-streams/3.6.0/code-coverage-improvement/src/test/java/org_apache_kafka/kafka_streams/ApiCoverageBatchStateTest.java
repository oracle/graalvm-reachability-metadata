/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.processor.internals.ClientUtils;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.streams.state.internals.metrics.StateStoreMetrics;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.streams.processor.internals.CorruptedRecord;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.state.internals.LeftOrRightValue;
import org.apache.kafka.streams.state.internals.LeftOrRightValueSerializer;
import org.apache.kafka.streams.state.internals.PrefixedSessionKeySchemas;
import org.apache.kafka.streams.state.internals.ThreadCache;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises state-key codecs, cache operations, and small internal value services through real round trips. */
public class ApiCoverageBatchStateTest {
    @Test
    void shouldRoundTripBothPrefixedSessionKeyLayouts() {
        Windowed<String> session = new Windowed<>("customer", new SessionWindow(10L, 30L));
        byte[] keyFirst = PrefixedSessionKeySchemas.KeyFirstSessionKeySchema.toBinary(
                session, Serdes.String().serializer(), "orders");
        byte[] timeFirst = PrefixedSessionKeySchemas.TimeFirstSessionKeySchema.toBinary(
                session, Serdes.String().serializer(), "orders");
        assertThat(PrefixedSessionKeySchemas.KeyFirstSessionKeySchema.from(
                keyFirst, Serdes.String().deserializer(), "orders")).isEqualTo(session);
        assertThat(PrefixedSessionKeySchemas.TimeFirstSessionKeySchema.from(
                timeFirst, Serdes.String().deserializer(), "orders")).isEqualTo(session);
        assertThat(PrefixedSessionKeySchemas.KeyFirstSessionKeySchema.from(Bytes.wrap(keyFirst))).isNotNull();
        assertThat(PrefixedSessionKeySchemas.TimeFirstSessionKeySchema.from(Bytes.wrap(timeFirst))).isNotNull();
    }

    @Test
    void shouldSerializeLeftAndRightValuesAndUseTheThreadCache() throws Exception {
        LeftOrRightValueSerializer<String, Integer> serializer =
                new LeftOrRightValueSerializer<>(Serdes.String().serializer(), Serdes.Integer().serializer());
        LeftOrRightValue<String, Integer> left = LeftOrRightValue.makeLeftValue("left");
        LeftOrRightValue<String, Integer> right = LeftOrRightValue.makeRightValue(7);
        assertThat(serializer.serialize("orders", left)).isNotEmpty();
        assertThat(serializer.serialize("orders", right)).isNotEmpty();
        serializer.close();

        StreamsMetricsImpl streamsMetrics = new StreamsMetricsImpl(new Metrics(), "api-client", "api-thread", Time.SYSTEM);
        ThreadCache cache = new ThreadCache(new LogContext("api-cache "), 1024L, streamsMetrics);
        String namespace = ThreadCache.nameSpaceFromTaskIdAndStore("0_0", "orders");
        assertThat(ThreadCache.taskIDfromCacheName(namespace)).isEqualTo("0_0");
        assertThat(ThreadCache.underlyingStoreNamefromCacheName(namespace)).isEqualTo("orders");
        Class<?> entryType = Class.forName("org.apache.kafka.streams.state.internals.LRUCacheEntry");
        Constructor<?> entryConstructor = entryType.getDeclaredConstructor(byte[].class);
        entryConstructor.setAccessible(true);
        Object entry = entryConstructor.newInstance(new byte[] {1});
        Method putIfAbsent = ThreadCache.class.getMethod("putIfAbsent", String.class, Bytes.class, entryType);
        Method get = ThreadCache.class.getMethod("get", String.class, Bytes.class);
        Method delete = ThreadCache.class.getMethod("delete", String.class, Bytes.class);
        assertThat(putIfAbsent.invoke(cache, namespace, Bytes.wrap(new byte[] {1}), entry)).isNull();
        assertThat(putIfAbsent.invoke(cache, namespace, Bytes.wrap(new byte[] {1}), entry)).isSameAs(entry);
        assertThat(cache.size()).isPositive();
        assertThat(get.invoke(cache, namespace, Bytes.wrap(new byte[] {1}))).isSameAs(entry);
        assertThat(delete.invoke(cache, namespace, Bytes.wrap(new byte[] {1}))).isSameAs(entry);
    }

    @Test
    void shouldCreateLatencyAndStateStoreSensors() {
        Metrics metrics = new Metrics();
        StreamsMetricsImpl streamsMetrics = new StreamsMetricsImpl(metrics, "sensor-client", "sensor-thread", Time.SYSTEM);
        Sensor latency = streamsMetrics.addLatencyRateTotalSensor("node", "operation", "latency",
                Sensor.RecordingLevel.INFO, "tag", "value");
        Sensor suppression = StateStoreMetrics.suppressionBufferSizeSensor("store", "task", "thread", streamsMetrics);
        assertThat(latency).isNotNull();
        assertThat(suppression).isNotNull();
        assertThat(streamsMetrics.metrics()).isNotEmpty();
        metrics.close();
    }

    @Test
    void shouldExposeClientIdentifiersAndCorruptedRecordIdentity() throws Exception {
        new ClientUtils();
        assertThat(ClientUtils.extractThreadId("StreamThread-7")).isEqualTo("StreamThread-7");
        assertThat(ClientUtils.getTaskProducerClientId("application", new TaskId(2, 3)))
                .contains("application", "2_3");
        assertThat(ClientUtils.getEndOffsets(KafkaFuture.completedFuture(Map.of()))).isEmpty();

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("orders", 1, 4L, new byte[] {1}, new byte[] {2});
        Constructor<CorruptedRecord> corruptedConstructor = CorruptedRecord.class.getDeclaredConstructor(ConsumerRecord.class);
        corruptedConstructor.setAccessible(true);
        CorruptedRecord corrupted = corruptedConstructor.newInstance(record);
        CorruptedRecord same = corruptedConstructor.newInstance(record);
        assertThat(corrupted).isEqualTo(same);
        assertThat(corrupted.hashCode()).isEqualTo(same.hashCode());
        assertThat(corrupted.toString()).contains("orders");
    }
}
