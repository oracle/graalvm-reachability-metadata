/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_api_events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.events.EventBuilder;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.events.EventEmitterBuilder;
import io.opentelemetry.api.events.EventEmitterProvider;
import io.opentelemetry.api.events.GlobalEventEmitterProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class Opentelemetry_api_eventsTest {

    @AfterEach
    void resetGlobalProvider() {
        GlobalEventEmitterProvider.resetForTest();
    }

    @Test
    void noopProviderBuildsEmitterThatAcceptsEvents() {
        EventEmitterProvider provider = EventEmitterProvider.noop();
        EventEmitterBuilder builder = provider.eventEmitterBuilder("io.example.noop");

        assertThat(builder.setEventDomain("example.audit")).isSameAs(builder);
        assertThat(builder.setSchemaUrl("https://example.test/schemas/events/1.0.0")).isSameAs(builder);
        assertThat(builder.setInstrumentationVersion("1.2.3")).isSameAs(builder);

        EventEmitter emitter = builder.build();

        Attributes attributes = Attributes.builder()
                .put("component", "authenticator")
                .put("attempt", 3L)
                .put("accepted", true)
                .build();
        assertThatCode(() -> {
            emitter.emit("user.login", attributes);
            emitter.emit("user.logout", Attributes.empty());
        }).doesNotThrowAnyException();
    }

    @Test
    void defaultGetCreatesEmitterThroughBuilder() {
        RecordingEventEmitterProvider provider = new RecordingEventEmitterProvider();

        EventEmitter emitter = provider.get("io.example.instrumentation");

        assertThat(provider.builders).hasSize(1);
        RecordingEventEmitterBuilder builder = provider.builders.get(0);
        assertThat(builder.instrumentationScopeName).isEqualTo("io.example.instrumentation");
        assertThat(builder.buildCount).isEqualTo(1);
        assertThat(emitter).isSameAs(builder.builtEmitters.get(0));
    }

    @Test
    void builderConfigurationIsRetainedByBuiltEmitter() {
        RecordingEventEmitterProvider provider = new RecordingEventEmitterProvider();

        EventEmitterBuilder builder = provider.eventEmitterBuilder("io.example.payment");
        assertThat(builder.setEventDomain("example.payment")).isSameAs(builder);
        assertThat(builder.setSchemaUrl("https://example.test/schema/payment/2.0.0")).isSameAs(builder);
        assertThat(builder.setInstrumentationVersion("2.4.6")).isSameAs(builder);

        RecordingEventEmitter emitter = (RecordingEventEmitter) builder.build();
        Attributes attributes = Attributes.builder()
                .put("payment.id", "pay-123")
                .put("payment.amount", 1299L)
                .put("payment.authorized", true)
                .build();

        emitter.emit("payment.authorized", attributes);

        assertThat(emitter.instrumentationScopeName).isEqualTo("io.example.payment");
        assertThat(emitter.eventDomain).isEqualTo("example.payment");
        assertThat(emitter.schemaUrl).isEqualTo("https://example.test/schema/payment/2.0.0");
        assertThat(emitter.instrumentationVersion).isEqualTo("2.4.6");
        assertThat(emitter.events).hasSize(1);
        RecordedEvent event = emitter.events.get(0);
        assertThat(event.name).isEqualTo("payment.authorized");
        assertThat(event.attributes).isSameAs(attributes);
    }

    @Test
    void eventBuilderSetsTimestampAndEmits() {
        RecordingEventEmitterProvider provider = new RecordingEventEmitterProvider();
        RecordingEventEmitter emitter = (RecordingEventEmitter) provider.get("io.example.builder");
        Attributes attributes = Attributes.builder()
                .put("payment.id", "pay-456")
                .put("payment.captured", true)
                .build();

        EventBuilder timestampBuilder = emitter.builder("payment.captured", attributes);
        assertThat(timestampBuilder.setTimestamp(1234L, TimeUnit.MILLISECONDS)).isSameAs(timestampBuilder);
        timestampBuilder.emit();

        Instant occurredAt = Instant.ofEpochSecond(5678L, 901L);
        EventBuilder instantBuilder = emitter.builder("payment.refunded", Attributes.empty());
        assertThat(instantBuilder.setTimestamp(occurredAt)).isSameAs(instantBuilder);
        instantBuilder.emit();

        assertThat(emitter.events).hasSize(2);
        RecordedEvent capturedEvent = emitter.events.get(0);
        assertThat(capturedEvent.name).isEqualTo("payment.captured");
        assertThat(capturedEvent.attributes).isSameAs(attributes);
        assertThat(capturedEvent.timestamp).isEqualTo(Instant.ofEpochMilli(1234L));
        RecordedEvent refundedEvent = emitter.events.get(1);
        assertThat(refundedEvent.name).isEqualTo("payment.refunded");
        assertThat(refundedEvent.attributes).isSameAs(Attributes.empty());
        assertThat(refundedEvent.timestamp).isEqualTo(occurredAt);
    }

    @Test
    void globalProviderCanBeRegisteredUsedAndReset() {
        RecordingEventEmitterProvider provider = new RecordingEventEmitterProvider();

        GlobalEventEmitterProvider.set(provider);

        assertThat(GlobalEventEmitterProvider.get()).isSameAs(provider);
        RecordingEventEmitter emitter = (RecordingEventEmitter) GlobalEventEmitterProvider.get()
                .eventEmitterBuilder("io.example.global")
                .setEventDomain("example.global")
                .build();
        emitter.emit("global.event", Attributes.empty());
        assertThat(emitter.events).extracting(event -> event.name).containsExactly("global.event");

        GlobalEventEmitterProvider.resetForTest();

        assertThat(GlobalEventEmitterProvider.get()).isNotSameAs(provider);
        assertThatCode(() -> GlobalEventEmitterProvider.get()
                .get("io.example.after-reset")
                .emit("after.reset", Attributes.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    void globalNoopRegistrationDoesNotClaimOrReplaceProvider() {
        EventEmitterProvider noopProvider = EventEmitterProvider.noop();
        RecordingEventEmitterProvider realProvider = new RecordingEventEmitterProvider();

        assertThatCode(() -> GlobalEventEmitterProvider.set(noopProvider)).doesNotThrowAnyException();
        assertThat(GlobalEventEmitterProvider.get()).isSameAs(noopProvider);

        GlobalEventEmitterProvider.set(realProvider);
        assertThat(GlobalEventEmitterProvider.get()).isSameAs(realProvider);

        assertThatCode(() -> GlobalEventEmitterProvider.set(noopProvider)).doesNotThrowAnyException();
        assertThat(GlobalEventEmitterProvider.get()).isSameAs(realProvider);
    }

    @Test
    void globalProviderRejectsSecondNonNoopRegistration() {
        RecordingEventEmitterProvider firstProvider = new RecordingEventEmitterProvider();
        RecordingEventEmitterProvider secondProvider = new RecordingEventEmitterProvider();

        GlobalEventEmitterProvider.set(firstProvider);

        assertThatThrownBy(() -> GlobalEventEmitterProvider.set(secondProvider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GlobalEventEmitterProvider.set has already been called")
                .hasMessageContaining("must be called only once");
        assertThat(GlobalEventEmitterProvider.get()).isSameAs(firstProvider);
    }

    @Test
    void resetForTestAllowsAnotherProviderToBeRegistered() {
        RecordingEventEmitterProvider firstProvider = new RecordingEventEmitterProvider();
        RecordingEventEmitterProvider secondProvider = new RecordingEventEmitterProvider();

        GlobalEventEmitterProvider.set(firstProvider);
        GlobalEventEmitterProvider.resetForTest();

        assertThatCode(() -> GlobalEventEmitterProvider.set(secondProvider)).doesNotThrowAnyException();
        assertThat(GlobalEventEmitterProvider.get()).isSameAs(secondProvider);

        RecordingEventEmitter emitter = (RecordingEventEmitter) GlobalEventEmitterProvider.get()
                .get("io.example.reconfigured");
        emitter.emit("provider.reconfigured", Attributes.empty());

        assertThat(secondProvider.builders).hasSize(1);
        assertThat(secondProvider.builders.get(0).instrumentationScopeName).isEqualTo("io.example.reconfigured");
        assertThat(emitter.events).extracting(event -> event.name).containsExactly("provider.reconfigured");
    }

    private static final class RecordingEventEmitterProvider implements EventEmitterProvider {
        private final List<RecordingEventEmitterBuilder> builders = new ArrayList<>();

        @Override
        public EventEmitterBuilder eventEmitterBuilder(String instrumentationScopeName) {
            RecordingEventEmitterBuilder builder = new RecordingEventEmitterBuilder(instrumentationScopeName);
            builders.add(builder);
            return builder;
        }
    }

    private static final class RecordingEventEmitterBuilder implements EventEmitterBuilder {
        private final String instrumentationScopeName;
        private final List<RecordingEventEmitter> builtEmitters = new ArrayList<>();
        private String eventDomain;
        private String schemaUrl;
        private String instrumentationVersion;
        private int buildCount;

        private RecordingEventEmitterBuilder(String instrumentationScopeName) {
            this.instrumentationScopeName = instrumentationScopeName;
        }

        @Override
        public EventEmitterBuilder setEventDomain(String eventDomain) {
            this.eventDomain = eventDomain;
            return this;
        }

        @Override
        public EventEmitterBuilder setSchemaUrl(String schemaUrl) {
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public EventEmitterBuilder setInstrumentationVersion(String instrumentationVersion) {
            this.instrumentationVersion = instrumentationVersion;
            return this;
        }

        @Override
        public EventEmitter build() {
            buildCount++;
            RecordingEventEmitter emitter = new RecordingEventEmitter(
                    instrumentationScopeName,
                    eventDomain,
                    schemaUrl,
                    instrumentationVersion);
            builtEmitters.add(emitter);
            return emitter;
        }
    }

    private static final class RecordingEventEmitter implements EventEmitter {
        private final String instrumentationScopeName;
        private final String eventDomain;
        private final String schemaUrl;
        private final String instrumentationVersion;
        private final List<RecordedEvent> events = new ArrayList<>();

        private RecordingEventEmitter(
                String instrumentationScopeName,
                String eventDomain,
                String schemaUrl,
                String instrumentationVersion) {
            this.instrumentationScopeName = instrumentationScopeName;
            this.eventDomain = eventDomain;
            this.schemaUrl = schemaUrl;
            this.instrumentationVersion = instrumentationVersion;
        }

        @Override
        public void emit(String eventName, Attributes attributes) {
            events.add(new RecordedEvent(eventName, attributes, null));
        }

        @Override
        public EventBuilder builder(String eventName, Attributes attributes) {
            return new RecordingEventBuilder(this, eventName, attributes);
        }
    }

    private static final class RecordingEventBuilder implements EventBuilder {
        private final RecordingEventEmitter emitter;
        private final String eventName;
        private final Attributes attributes;
        private Instant timestamp;

        private RecordingEventBuilder(
                RecordingEventEmitter emitter,
                String eventName,
                Attributes attributes) {
            this.emitter = emitter;
            this.eventName = eventName;
            this.attributes = attributes;
        }

        @Override
        public EventBuilder setTimestamp(long timestamp, TimeUnit unit) {
            this.timestamp = Instant.ofEpochSecond(0L, unit.toNanos(timestamp));
            return this;
        }

        @Override
        public EventBuilder setTimestamp(Instant instant) {
            timestamp = instant;
            return this;
        }

        @Override
        public void emit() {
            emitter.events.add(new RecordedEvent(eventName, attributes, timestamp));
        }
    }

    private static final class RecordedEvent {
        private final String name;
        private final Attributes attributes;
        private final Instant timestamp;

        private RecordedEvent(String name, Attributes attributes, Instant timestamp) {
            this.name = name;
            this.attributes = attributes;
            this.timestamp = timestamp;
        }
    }
}
