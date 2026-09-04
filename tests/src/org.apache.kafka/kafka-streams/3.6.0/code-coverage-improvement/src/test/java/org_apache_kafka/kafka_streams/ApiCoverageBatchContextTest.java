/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.processor.internals.ForwardingDisabledProcessorContext;
import org.apache.kafka.streams.processor.internals.StoreToProcessorContextAdapter;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.test.InternalMockProcessorContext;
import org.apache.kafka.streams.state.StateSerdes;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies delegation and metadata behavior of processor contexts used by user processors. */
public class ApiCoverageBatchContextTest {
    @Test
    void shouldDelegateEveryReadableProcessorContextProperty() {
        NativeCoverageFixtures.SimpleProcessorContext delegate =
                new NativeCoverageFixtures.SimpleProcessorContext(99L);
        ForwardingDisabledProcessorContext context = new ForwardingDisabledProcessorContext(delegate);

        assertThat(context.applicationId()).isEqualTo("coverage");
        assertThat(context.taskId()).isEqualTo(new TaskId(0, 0));
        assertThat(context.keySerde()).isInstanceOf(Serdes.StringSerde.class);
        assertThat(context.valueSerde()).isInstanceOf(Serdes.StringSerde.class);
        assertThat(context.stateDir()).isEqualTo(new File("build/native-coverage-state"));
        assertThat(context.metrics()).isNull();
        assertThat(context.topic()).isEqualTo("orders");
        assertThat(context.partition()).isEqualTo(3);
        assertThat(context.offset()).isEqualTo(17L);
        assertThat(context.headers()).isNotNull();
        assertThat(context.timestamp()).isEqualTo(99L);
        assertThat(context.appConfigs()).containsEntry("prefix.key", "value");
        assertThat(context.appConfigsWithPrefix("prefix.")).containsEntry("key", "value");
        assertThat(context.currentSystemTimeMs()).isEqualTo(1000L);
        assertThat(context.currentStreamTimeMs()).isEqualTo(900L);
        context.commit();
        StateStore store = org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore("registered").get();
        context.register(store, (key, value) -> { });
        assertThat(delegate.committed()).isTrue();
        assertThatThrownBy(() -> context.forward("key", "value")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> context.forward("key", "value", To.all())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldAdaptStateStoreContextToLegacyProcessorContext() {
        ProcessorContext context = StoreToProcessorContextAdapter.adapt(NativeCoverageFixtures.stateStoreContext());
        assertThat(context.applicationId()).isEqualTo("coverage");
        assertThat(context.taskId()).isEqualTo(new TaskId(0, 0));
        assertThat(context.keySerde()).isInstanceOf(Serdes.StringSerde.class);
        assertThat(context.valueSerde()).isInstanceOf(Serdes.StringSerde.class);
        assertThat(context.stateDir()).isEqualTo(new File("build/native-coverage-state"));
        assertThat(context.metrics()).isNull();
        assertThat(context.appConfigs()).containsEntry("adapter.key", "value");
        assertThat(context.appConfigsWithPrefix("adapter.")).containsEntry("key", "value");
        assertThatThrownBy(context::topic).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(context::partition).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(context::offset).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(context::timestamp).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(context::headers).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(context::currentSystemTimeMs).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(context::currentStreamTimeMs).isInstanceOf(UnsupportedOperationException.class);
        context.register(Stores.inMemoryKeyValueStore("adapter-registered").get(), (key, value) -> { });
        assertThatThrownBy(context::commit).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRunProcessorMetadataThroughTheMockContext() {
        InternalMockProcessorContext<?, ?> context = new InternalMockProcessorContext<>(
                StateSerdes.withBuiltinTypes("metadata", String.class, String.class), NativeCoverageFixtures.recordCollector());
        context.initialize();
        context.addProcessorMetadataKeyValue("orders", 42L);
        assertThat(context.processorMetadataForKey("orders")).isEqualTo(42L);
        assertThat(context.appConfigsWithPrefix("")).isNotNull();
        assertThat(context.metrics()).isNotNull();
    }

    @Test
    void shouldPassSchedulingToAProcessorContext() {
        NativeCoverageFixtures.SimpleProcessorContext delegate =
                new NativeCoverageFixtures.SimpleProcessorContext(99L);
        ForwardingDisabledProcessorContext context = new ForwardingDisabledProcessorContext(delegate);
        assertThat(context.schedule(Duration.ofSeconds(1), PunctuationType.STREAM_TIME,
                timestamp -> { })).isNotNull();
    }
}
