/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.ForeachProcessor;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.apache.kafka.streams.kstream.internals.KStreamFlatTransformValues;
import org.apache.kafka.streams.kstream.internals.KStreamTransformValues;
import org.apache.kafka.streams.kstream.internals.KStreamPrint;
import org.apache.kafka.streams.kstream.internals.StreamStreamJoinUtil;
import org.apache.kafka.streams.kstream.internals.TransformerSupplierAdapter;
import org.apache.kafka.streams.kstream.internals.WrappingNullableUtils;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.internals.DefaultStreamPartitioner;
import org.apache.kafka.streams.kstream.TimeWindowedSerializer;
import org.apache.kafka.streams.kstream.internals.WindowedStreamPartitioner;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.junit.jupiter.api.Test;
import org.apache.kafka.test.InternalMockProcessorContext;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs processor suppliers through records and checks their public lifecycle contracts. */
public class ApiCoverageBatchProcessorTest {
    @Test
    void shouldProcessFlatValuesAndForeachRecords() {
        ValueTransformerWithKeySupplier<String, String, Iterable<String>> supplier = () ->
                new ValueTransformerWithKey<>() {
                    @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
                    @Override public Iterable<String> transform(String key, String value) {
                        return List.of(key + value, value);
                    }
                    @Override public void close() { }
                };
        KStreamFlatTransformValues<String, String, String> flatValues = new KStreamFlatTransformValues<>(supplier);
        Processor<String, String, String, String> processor = flatValues.get();
        org.apache.kafka.test.InternalMockProcessorContext<String, String> context =
                new org.apache.kafka.test.InternalMockProcessorContext<>(new java.io.File("build/api-flat"),
                        Serdes.String(), Serdes.String(), NativeCoverageFixtures.recordCollector(), null);
        context.initialize();
        processor.init(context);
        processor.process(new Record<>("k", "v", 10L));
        processor.close();
        assertThat(flatValues.stores()).isNull();

        ForeachProcessor<String, String> foreach = new ForeachProcessor<>((key, value) -> assertThat(value).isEqualTo("v"));
        foreach.process(new Record<>("k", "v", 10L));
        KStreamPrint<String, String> print = new KStreamPrint<>((key, value) -> assertThat(key).isEqualTo("k"));
        Processor<String, String, Void, Void> printProcessor = print.get();
        printProcessor.init(new InternalMockProcessorContext<>());
        printProcessor.process(new Record<>("k", "v", 10L));
        printProcessor.close();
        assertThat(print.get()).isNotNull();
    }

    @Test
    void shouldTransformValuesThroughTheProcessorLifecycle() throws Exception {
        AtomicBoolean initialized = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        ValueTransformerWithKeySupplier<String, String, String> supplier = () -> new ValueTransformerWithKey<>() {
            @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) {
                initialized.set(true);
            }
            @Override public String transform(String key, String value) {
                assertThat(key).isEqualTo("customer");
                assertThat(value).isEqualTo("pending");
                return "ready";
            }
            @Override public void close() {
                closed.set(true);
            }
        };
        Constructor<KStreamTransformValues> constructor = KStreamTransformValues.class.getDeclaredConstructor(
                ValueTransformerWithKeySupplier.class);
        constructor.setAccessible(true);
        KStreamTransformValues<String, String, String> stage = constructor.newInstance(supplier);
        Processor<String, String, String, String> processor = stage.get();
        org.apache.kafka.test.InternalMockProcessorContext<String, String> context =
                new org.apache.kafka.test.InternalMockProcessorContext<>(
                        new java.io.File("build/api-transform-values"), Serdes.String(), Serdes.String(),
                        NativeCoverageFixtures.recordCollector(), null);
        context.initialize();
        processor.init(context);
        processor.process(new Record<>("customer", "pending", 10L));
        processor.close();
        assertThat(stage.stores()).isNull();
        assertThat(initialized).isTrue();
        assertThat(closed).isTrue();
    }

    @Test
    void shouldAdaptLegacyTransformersAndPartitionWindowedKeys() {
        org.apache.kafka.streams.kstream.TransformerSupplier<String, String, KeyValue<String, String>> legacy =
                () -> new org.apache.kafka.streams.kstream.Transformer<>() {
                    @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
                    @Override public KeyValue<String, String> transform(String key, String value) {
                        return KeyValue.pair(key, value);
                    }
                    @Override public void close() { }
                };
        TransformerSupplierAdapter<String, String, String, String> adapter = new TransformerSupplierAdapter<>(legacy);
        assertThat(adapter.get().transform("key", "value")).isNotNull();
        assertThat(adapter.stores()).isNull();
        new WrappingNullableUtils();

        DefaultStreamPartitioner<String, String> partitioner = new DefaultStreamPartitioner<>(Serdes.String().serializer());
        assertThat(partitioner.partition("orders", "key", "value", 4)).isBetween(0, 3);
        TimeWindowedSerializer<String> windowedSerializer = new TimeWindowedSerializer<>(Serdes.String().serializer());
        WindowedStreamPartitioner<String, String> windowedPartitioner = new WindowedStreamPartitioner<>(windowedSerializer);
        Windowed<String> windowed = new Windowed<>("key", new TimeWindow(1, 2));
        assertThat(windowedPartitioner.partition("orders", windowed, "value", 4)).isBetween(0, 3);
        org.apache.kafka.streams.processor.StreamPartitioner rawPartitioner = windowedPartitioner;
        assertThat((Integer) rawPartitioner.partition("orders", windowed, "value", 4)).isBetween(0, 3);
    }

    @Test
    void shouldInvokeDefaultProcessorLifecycleAndRejectSkippedJoinRecords() throws Exception {
        FixedKeyProcessor<String, String, String> fixed = new FixedKeyProcessor<>() {
            @Override public void process(FixedKeyRecord<String, String> record) {
                assertThat(record.value()).isEqualTo("value");
            }
        };
        fixed.init(new InternalMockProcessorContext<>());
        Constructor<FixedKeyRecord> constructor = FixedKeyRecord.class.getDeclaredConstructor(
                Object.class, Object.class, long.class, org.apache.kafka.common.header.Headers.class);
        constructor.setAccessible(true);
        fixed.process(constructor.newInstance("key", "value", 1L, null));
        fixed.close();
        Processor<String, String, String, String> processor = new Processor<>() {
            @Override public void process(Record<String, String> record) {
                assertThat(record.key()).isEqualTo("key");
            }
        };
        processor.init(new InternalMockProcessorContext<>());
        processor.process(new Record<>("key", "value", 1L));
        processor.close();
        org.apache.kafka.common.metrics.Metrics metrics = new org.apache.kafka.common.metrics.Metrics();
        assertThat(StreamStreamJoinUtil.skipRecord(new Record<>(null, "value", 1L),
                org.slf4j.LoggerFactory.getLogger(ApiCoverageBatchProcessorTest.class), metrics.sensor("join"),
                new InternalMockProcessorContext<>())).isTrue();
        metrics.close();
    }
}
