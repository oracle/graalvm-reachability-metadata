/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_tracing_opentelemetry;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper.ScopeAttachedEvent;
import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper.ScopeClosedEvent;
import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper.ScopeRestoredEvent;
import io.micrometer.tracing.otel.bridge.OtelTracer.EventPublisher;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryEventPublisherBeansApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenTelemetryEventPublisherBeansApplicationListenerTest {

    @Test
    void publishesScopeEventsForEventPublisherBeansInAnActiveContext() {
        OpenTelemetryEventPublisherBeansApplicationListener listener =
                new OpenTelemetryEventPublisherBeansApplicationListener();
        OpenTelemetryEventPublisherBeansApplicationListener.addWrapper();
        List<Object> events = new ArrayList<>();
        EventPublisher publisher = events::add;
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("recordingEventPublisher", publisher);
        applicationContext.refresh();

        assertThat(listener.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(listener.supportsEventType(ResolvableType.forClass(ContextRefreshedEvent.class))).isTrue();
        assertThat(listener.supportsEventType(ResolvableType.forClass(ContextClosedEvent.class))).isTrue();
        assertThat(listener.supportsEventType(ResolvableType.forClass(String.class))).isFalse();

        listener.onApplicationEvent(new ContextRefreshedEvent(applicationContext));
        ContextKey<String> requestKey = ContextKey.named("request");
        try {
            try (Scope ignored = Context.root().with(requestKey, "trace-request").makeCurrent()) {
                assertThat(Context.current().get(requestKey)).isEqualTo("trace-request");
            }

            assertThat(events).hasSize(3);
            assertThat(events.get(0)).isInstanceOf(ScopeAttachedEvent.class);
            assertThat(events.get(1)).isInstanceOf(ScopeClosedEvent.class);
            assertThat(events.get(2)).isInstanceOf(ScopeRestoredEvent.class);
        } finally {
            listener.onApplicationEvent(new ContextClosedEvent(applicationContext));
            applicationContext.close();
        }
    }

}
