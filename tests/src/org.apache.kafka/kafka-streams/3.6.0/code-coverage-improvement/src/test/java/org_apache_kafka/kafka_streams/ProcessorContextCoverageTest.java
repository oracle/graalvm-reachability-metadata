/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.processor.internals.ForwardingDisabledProcessorContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessorContextCoverageTest {
    @Test
    void forwardingDisabledContextDelegatesRecordAndLifecycleOperations() {
        ProcessorContext delegate = mock(ProcessorContext.class);
        Cancellable cancellable = mock(Cancellable.class);
        StateStore store = mock(StateStore.class);
        Headers headers = new RecordHeaders().add("trace", new byte[] {1});
        when(delegate.schedule(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(cancellable);
        when(delegate.getStateStore("store")).thenReturn(store);
        when(delegate.appConfigsWithPrefix("client.")).thenReturn(Map.of("id", "one"));
        when(delegate.headers()).thenReturn(headers);
        when(delegate.topic()).thenReturn("input");
        when(delegate.partition()).thenReturn(2);
        when(delegate.offset()).thenReturn(9L);
        when(delegate.currentStreamTimeMs()).thenReturn(11L);
        ForwardingDisabledProcessorContext context = new ForwardingDisabledProcessorContext(delegate);

        assertThat(context.schedule(Duration.ofSeconds(1), PunctuationType.STREAM_TIME, timestamp -> { }))
                .isSameAs(cancellable);
        assertThat((StateStore) context.getStateStore("store")).isSameAs(store);
        assertThat(context.appConfigsWithPrefix("client.")).containsEntry("id", "one");
        assertThat(context.headers()).isSameAs(headers);
        assertThat(context.topic()).isEqualTo("input");
        assertThat(context.partition()).isEqualTo(2);
        assertThat(context.offset()).isEqualTo(9L);
        assertThat(context.currentStreamTimeMs()).isEqualTo(11L);
        context.commit();
        verify(delegate).commit();
        assertThatThrownBy(() -> context.forward("key", "value")).isInstanceOf(org.apache.kafka.streams.errors.StreamsException.class);
    }
}
